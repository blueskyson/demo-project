package com.example.esgaward.service;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.esgaward.dto.AddMemberRequest;
import com.example.esgaward.dto.CreateProposalRequest;
import com.example.esgaward.dto.ProposalDetailDto;
import com.example.esgaward.dto.ProposalSummaryDto;
import com.example.esgaward.dto.UpdateProposalRequest;
import com.example.esgaward.entity.AwardEvent;
import com.example.esgaward.entity.Proposal;
import com.example.esgaward.entity.User;
import com.example.esgaward.exception.ConflictException;
import com.example.esgaward.exception.NotFoundException;
import com.example.esgaward.openfga.RelationshipTuples;
import com.example.esgaward.repository.ProposalRepository;
import com.example.esgaward.repository.UserRepository;
import com.example.esgaward.security.AuthorizationService;
import com.example.esgaward.security.CurrentUserService;
import com.example.esgaward.security.Permission;
import com.example.esgaward.security.RequirePermission;
import com.example.esgaward.security.ResourceId;
import com.example.esgaward.storage.FileStorage;

@Service
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final UserRepository userRepository;
    private final AwardEventService awardEventService;
    private final CurrentUserService currentUserService;
    private final AuthorizationService authorizationService;
    private final RelationshipTuples relationshipTuples;
    private final FileStorage fileStorage;
    private final Clock clock;

    public ProposalService(ProposalRepository proposalRepository, UserRepository userRepository,
            AwardEventService awardEventService, CurrentUserService currentUserService,
            AuthorizationService authorizationService, RelationshipTuples relationshipTuples, FileStorage fileStorage,
            Clock clock) {
        this.proposalRepository = proposalRepository;
        this.userRepository = userRepository;
        this.awardEventService = awardEventService;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
        this.relationshipTuples = relationshipTuples;
        this.fileStorage = fileStorage;
        this.clock = clock;
    }

    /** Admins see every proposal; normal users see the ones OpenFGA says they can view. */
    @Transactional
    @RequirePermission(Permission.PROPOSAL_LIST)
    public List<ProposalSummaryDto> list(Long awardEventId) {
        User user = currentUserService.currentUser();
        List<Proposal> proposals = user.isAdmin()
                ? proposalRepository.findAllByEvent(awardEventId)
                : findByIds(authorizationService.viewableProposalIds(user), awardEventId);
        Predicate<Long> editable = authorizationService.proposalEditability(user);
        return proposals.stream()
                .map(p -> ProposalSummaryDto.from(p, editable.test(p.getId())))
                .toList();
    }

    @Transactional
    @RequirePermission(Permission.PROPOSAL_READ)
    public ProposalDetailDto get(@ResourceId Long id) {
        return toDetail(currentUserService.currentUser(), find(id));
    }

    @Transactional
    @RequirePermission(Permission.PROPOSAL_CREATE)
    public ProposalDetailDto create(@ResourceId Long awardEventId, CreateProposalRequest request) {
        User user = currentUserService.currentUser();
        AwardEvent awardEvent = awardEventService.find(awardEventId);
        User leader = resolveLeader(user, request.leaderId(), user);

        Proposal proposal = proposalRepository.save(
                new Proposal(awardEvent, leader, request.title(), request.description(), clock.instant()));
        relationshipTuples.proposalCreated(proposal);
        return toDetail(user, proposal);
    }

    @Transactional
    @RequirePermission(Permission.PROPOSAL_UPDATE)
    public ProposalDetailDto update(@ResourceId Long id, UpdateProposalRequest request) {
        User user = currentUserService.currentUser();
        Proposal proposal = find(id);
        User oldLeader = proposal.getLeader();
        User leader = resolveLeader(user, request.leaderId(), oldLeader);
        if (proposal.hasMember(leader.getId())) {
            throw new ConflictException("The new leader is currently a member; remove them from members first");
        }

        proposal.update(request.title(), request.description(), leader, clock.instant());
        if (!leader.getId().equals(oldLeader.getId())) {
            relationshipTuples.leaderChanged(id, oldLeader.getId(), leader.getId());
        }
        return toDetail(user, proposal);
    }

    @Transactional
    @RequirePermission(Permission.PROPOSAL_DELETE)
    public void delete(@ResourceId Long id) {
        Proposal proposal = find(id);
        proposal.getFiles().forEach(file -> fileStorage.deleteAfterCommit(file.getStorageKey()));
        relationshipTuples.proposalDeleted(proposal);
        proposalRepository.delete(proposal);
    }

    @Transactional
    @RequirePermission(Permission.PROPOSAL_MEMBER_MANAGE)
    public ProposalDetailDto addMember(@ResourceId Long id, AddMemberRequest request) {
        Proposal proposal = find(id);
        User member = findUser(request.userId());
        if (proposal.isLedBy(member)) {
            throw new ConflictException("The leader cannot also be added as a member");
        }
        if (proposal.hasMember(member.getId())) {
            throw new ConflictException(member.getUsername() + " is already a member");
        }
        proposal.addMember(member, clock.instant());
        relationshipTuples.memberAdded(id, member.getId());
        return toDetail(currentUserService.currentUser(), proposal);
    }

    @Transactional
    @RequirePermission(Permission.PROPOSAL_MEMBER_MANAGE)
    public ProposalDetailDto removeMember(@ResourceId Long id, UUID memberId) {
        Proposal proposal = find(id);
        if (!proposal.removeMember(memberId, clock.instant())) {
            throw new NotFoundException("User " + memberId + " is not a member of proposal " + id);
        }
        relationshipTuples.memberRemoved(id, memberId);
        return toDetail(currentUserService.currentUser(), proposal);
    }

    private List<Proposal> findByIds(Set<Long> ids, Long awardEventId) {
        return ids.isEmpty() ? List.of() : proposalRepository.findAllByIdInAndEvent(ids, awardEventId);
    }

    Proposal find(Long id) {
        return proposalRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Proposal " + id + " not found"));
    }

    /**
     * Only admins may pick a leader other than the default. This depends on the request body, so
     * it's checked here rather than through a {@link Permission}.
     */
    private User resolveLeader(User currentUser, UUID requestedLeaderId, User defaultLeader) {
        if (requestedLeaderId == null || requestedLeaderId.equals(defaultLeader.getId())) {
            return defaultLeader;
        }
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Only admins can assign the proposal leader");
        }
        return findUser(requestedLeaderId);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
    }

    private ProposalDetailDto toDetail(User user, Proposal proposal) {
        return ProposalDetailDto.from(proposal, authorizationService.canEdit(user, proposal), clock.instant());
    }
}

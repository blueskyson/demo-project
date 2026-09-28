package com.example.esgaward.proposal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.esgaward.awardevent.AwardEvent;
import com.example.esgaward.awardevent.AwardEventService;
import com.example.esgaward.common.ConflictException;
import com.example.esgaward.common.NotFoundException;
import com.example.esgaward.security.AccessPolicy;
import com.example.esgaward.storage.FileStorage;
import com.example.esgaward.user.CurrentUserService;
import com.example.esgaward.user.User;
import com.example.esgaward.user.UserRepository;

@Service
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final UserRepository userRepository;
    private final AwardEventService awardEventService;
    private final CurrentUserService currentUserService;
    private final AccessPolicy accessPolicy;
    private final FileStorage fileStorage;
    private final Clock clock;

    public ProposalService(ProposalRepository proposalRepository, UserRepository userRepository,
            AwardEventService awardEventService, CurrentUserService currentUserService, AccessPolicy accessPolicy,
            FileStorage fileStorage, Clock clock) {
        this.proposalRepository = proposalRepository;
        this.userRepository = userRepository;
        this.awardEventService = awardEventService;
        this.currentUserService = currentUserService;
        this.accessPolicy = accessPolicy;
        this.fileStorage = fileStorage;
        this.clock = clock;
    }

    /** Admins see every proposal; normal users see the ones they lead or are a member of. */
    @Transactional
    public List<ProposalSummaryDto> list(Long awardEventId) {
        User user = currentUserService.currentUser();
        List<Proposal> proposals = user.isAdmin()
                ? proposalRepository.findAllByEvent(awardEventId)
                : proposalRepository.findAllVisibleTo(user.getId(), awardEventId);
        return proposals.stream()
                .map(p -> ProposalSummaryDto.from(p, accessPolicy.canEdit(user, p)))
                .toList();
    }

    @Transactional
    public ProposalDetailDto get(Long id) {
        User user = currentUserService.currentUser();
        Proposal proposal = find(id);
        accessPolicy.checkView(user, proposal);
        return toDetail(user, proposal);
    }

    @Transactional
    public ProposalDetailDto create(CreateProposalRequest request) {
        User user = currentUserService.currentUser();
        AwardEvent awardEvent = awardEventService.find(request.awardEventId());
        accessPolicy.checkCreateProposalIn(user, awardEvent);
        User leader = resolveLeader(user, request.leaderId(), user);

        Proposal proposal = proposalRepository.save(
                new Proposal(awardEvent, leader, request.title(), request.description(), clock.instant()));
        return toDetail(user, proposal);
    }

    @Transactional
    public ProposalDetailDto update(Long id, UpdateProposalRequest request) {
        User user = currentUserService.currentUser();
        Proposal proposal = find(id);
        accessPolicy.checkEdit(user, proposal);
        User leader = resolveLeader(user, request.leaderId(), proposal.getLeader());
        if (proposal.hasMember(leader.getId())) {
            throw new ConflictException("The new leader is currently a member; remove them from members first");
        }

        proposal.update(request.title(), request.description(), leader, clock.instant());
        return toDetail(user, proposal);
    }

    @Transactional
    public void delete(Long id) {
        User user = currentUserService.currentUser();
        Proposal proposal = find(id);
        accessPolicy.checkEdit(user, proposal);

        proposal.getFiles().forEach(file -> fileStorage.deleteAfterCommit(file.getStorageKey()));
        proposalRepository.delete(proposal);
    }

    @Transactional
    public ProposalDetailDto addMember(Long id, AddMemberRequest request) {
        User user = currentUserService.currentUser();
        Proposal proposal = find(id);
        accessPolicy.checkEdit(user, proposal);

        User member = findUser(request.userId());
        if (proposal.isLedBy(member)) {
            throw new ConflictException("The leader cannot also be added as a member");
        }
        if (proposal.hasMember(member.getId())) {
            throw new ConflictException(member.getUsername() + " is already a member");
        }
        proposal.addMember(member, clock.instant());
        return toDetail(user, proposal);
    }

    @Transactional
    public ProposalDetailDto removeMember(Long id, UUID memberId) {
        User user = currentUserService.currentUser();
        Proposal proposal = find(id);
        accessPolicy.checkEdit(user, proposal);

        if (!proposal.removeMember(memberId, clock.instant())) {
            throw new NotFoundException("User " + memberId + " is not a member of proposal " + id);
        }
        return toDetail(user, proposal);
    }

    Proposal find(Long id) {
        return proposalRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Proposal " + id + " not found"));
    }

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
        return ProposalDetailDto.from(proposal, accessPolicy.canEdit(user, proposal), clock.instant());
    }
}

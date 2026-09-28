package com.example.esgaward.security;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.example.esgaward.entity.Proposal;
import com.example.esgaward.entity.User;
import com.example.esgaward.exception.NotFoundException;
import com.example.esgaward.openfga.Fga;
import com.example.esgaward.openfga.OpenFgaGateway;
import com.example.esgaward.repository.AwardEventRepository;
import com.example.esgaward.repository.ProposalRepository;

import dev.openfga.sdk.api.client.model.ClientTupleKey;

/**
 * Answers "may the current user do X?" by asking OpenFGA. The rules themselves live in
 * {@code openfga/model.fga}:
 * <ul>
 *   <li>{@code ADMIN} may do anything.</li>
 *   <li>{@code NORMAL_USER} may edit a proposal (and its members/files) only when they are its
 *       leader <em>and</em> the proposal's award event has not passed its deadline.</li>
 *   <li>{@code NORMAL_USER} may view proposals they lead or are a member of.</li>
 * </ul>
 * Each check sends two extra inputs:
 * <ul>
 *   <li>a contextual tuple {@code system:esg-award#admin@user:<id>} when the caller's Keycloak token
 *       has the admin role — so Keycloak remains the only source of truth for roles;</li>
 *   <li>{@code current_time}, evaluated against each award event's {@code before_deadline} condition.</li>
 * </ul>
 */
@Component
public class AuthorizationService {

    private static final String ADMIN_ONLY = "Only admins can perform this action";
    private static final String DEADLINE_PASSED = "The award event deadline has passed";
    private static final String NOT_LEADER_OR_MEMBER = "You are not the leader or a member of this proposal";
    private static final String NOT_LEADER = "Only the proposal leader can edit this proposal";

    private final CurrentUserService currentUserService;
    private final OpenFgaGateway fga;
    private final AwardEventRepository awardEventRepository;
    private final ProposalRepository proposalRepository;
    private final Clock clock;

    public AuthorizationService(CurrentUserService currentUserService, OpenFgaGateway fga,
            AwardEventRepository awardEventRepository, ProposalRepository proposalRepository, Clock clock) {
        this.currentUserService = currentUserService;
        this.fga = fga;
        this.awardEventRepository = awardEventRepository;
        this.proposalRepository = proposalRepository;
        this.clock = clock;
    }

    /**
     * Throws {@link AccessDeniedException} unless the current user holds {@code permission} on the
     * resource, or {@link NotFoundException} if the resource doesn't exist.
     */
    public void check(Permission permission, Long resourceId) {
        User user = currentUserService.currentUser();
        // A switch expression must cover every enum constant, so adding a Permission without
        // mapping it to an OpenFGA relation is a compile error. A null reason means "allowed".
        String denialReason = switch (permission) {
            // Any user with an app role (already enforced by SecurityConfig).
            case USER_READ, AWARD_EVENT_LIST, PROPOSAL_LIST -> null;
            case AWARD_EVENT_CREATE -> allowedOrElse(check(user, Fga.CAN_CREATE_AWARD_EVENT, Fga.SYSTEM), ADMIN_ONLY);
            case AWARD_EVENT_READ -> allowedOrElse(checkAwardEvent(user, Fga.CAN_VIEW, resourceId), ADMIN_ONLY);
            case AWARD_EVENT_UPDATE, AWARD_EVENT_DELETE ->
                    allowedOrElse(checkAwardEvent(user, Fga.CAN_MANAGE, resourceId), ADMIN_ONLY);
            case PROPOSAL_CREATE ->
                    allowedOrElse(checkAwardEvent(user, Fga.CAN_CREATE_PROPOSAL, resourceId), DEADLINE_PASSED);
            case PROPOSAL_READ, PROPOSAL_FILE_READ ->
                    allowedOrElse(checkProposal(user, Fga.CAN_VIEW, resourceId), NOT_LEADER_OR_MEMBER);
            case PROPOSAL_UPDATE, PROPOSAL_DELETE, PROPOSAL_MEMBER_MANAGE, PROPOSAL_FILE_UPLOAD,
                    PROPOSAL_FILE_DELETE -> checkProposal(user, Fga.CAN_EDIT, resourceId)
                            ? null
                            : editDenialReason(user, resourceId);
        };
        if (denialReason != null) {
            throw new AccessDeniedException(denialReason);
        }
    }

    /** Used for the {@code editable} flag in responses, so the UI matches the enforced rule. */
    public boolean canEdit(User user, Proposal proposal) {
        return check(user, Fga.CAN_EDIT, Fga.proposal(proposal.getId()));
    }

    /** Ids of the proposals the user may view (OpenFGA ListObjects). */
    public Set<Long> viewableProposalIds(User user) {
        return listProposalIds(user, Fga.CAN_VIEW);
    }

    /** Whether the user may edit a proposal id; one ListObjects call instead of one check per proposal. */
    public Predicate<Long> proposalEditability(User user) {
        if (user.isAdmin()) {
            return id -> true;
        }
        return listProposalIds(user, Fga.CAN_EDIT)::contains;
    }

    private boolean checkAwardEvent(User user, String relation, Long id) {
        if (!awardEventRepository.existsById(id)) {
            throw new NotFoundException("Award event " + id + " not found");
        }
        return check(user, relation, Fga.awardEvent(id));
    }

    private boolean checkProposal(User user, String relation, Long id) {
        if (!proposalRepository.existsById(id)) {
            throw new NotFoundException("Proposal " + id + " not found");
        }
        return check(user, relation, Fga.proposal(id));
    }

    /** A leader can only be denied because the deadline passed; everyone else isn't the leader. */
    private String editDenialReason(User user, Long proposalId) {
        return check(user, Fga.LEADER, Fga.proposal(proposalId)) ? DEADLINE_PASSED : NOT_LEADER;
    }

    private boolean check(User user, String relation, String object) {
        return fga.check(Fga.user(user.getId()), relation, object, contextualTuples(user), context());
    }

    private Set<Long> listProposalIds(User user, String relation) {
        return fga.listObjects(Fga.user(user.getId()), relation, Fga.PROPOSAL_TYPE, contextualTuples(user), context())
                .stream()
                .map(Fga::idOf)
                .collect(Collectors.toSet());
    }

    private static List<ClientTupleKey> contextualTuples(User user) {
        if (!user.isAdmin()) {
            return List.of();
        }
        return List.of(new ClientTupleKey().user(Fga.user(user.getId())).relation(Fga.ADMIN)._object(Fga.SYSTEM));
    }

    private Map<String, Object> context() {
        return Map.of(Fga.CURRENT_TIME_PARAM, clock.instant().toString());
    }

    private static String allowedOrElse(boolean allowed, String denialReason) {
        return allowed ? null : denialReason;
    }
}

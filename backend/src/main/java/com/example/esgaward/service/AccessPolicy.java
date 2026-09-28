package com.example.esgaward.service;

import java.time.Clock;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.example.esgaward.entity.AwardEvent;
import com.example.esgaward.entity.Proposal;
import com.example.esgaward.entity.User;

/**
 * Single place for the resource-level authorization rules:
 * <ul>
 *   <li>{@code ADMIN} may do anything.</li>
 *   <li>{@code NORMAL_USER} may edit a proposal (and its members/files) only when they are its
 *       leader <em>and</em> the proposal's award event has not passed its deadline.</li>
 *   <li>{@code NORMAL_USER} may view proposals they lead or are a member of.</li>
 * </ul>
 * Role-only rules (e.g. "only admins manage award events") are declared with {@code @PreAuthorize}
 * on the controllers instead.
 */
@Component
public class AccessPolicy {

    private final Clock clock;

    public AccessPolicy(Clock clock) {
        this.clock = clock;
    }

    public boolean canView(User user, Proposal proposal) {
        return user.isAdmin() || proposal.isLedBy(user) || proposal.hasMember(user.getId());
    }

    public boolean canEdit(User user, Proposal proposal) {
        return user.isAdmin() || (proposal.isLedBy(user) && isOpen(proposal.getAwardEvent()));
    }

    public boolean canCreateProposalIn(User user, AwardEvent awardEvent) {
        return user.isAdmin() || isOpen(awardEvent);
    }

    public void checkView(User user, Proposal proposal) {
        if (!canView(user, proposal)) {
            throw new AccessDeniedException("You are not the leader or a member of this proposal");
        }
    }

    public void checkEdit(User user, Proposal proposal) {
        if (canEdit(user, proposal)) {
            return;
        }
        if (!proposal.isLedBy(user)) {
            throw new AccessDeniedException("Only the proposal leader can edit this proposal");
        }
        throw new AccessDeniedException("The award event deadline has passed");
    }

    public void checkCreateProposalIn(User user, AwardEvent awardEvent) {
        if (!canCreateProposalIn(user, awardEvent)) {
            throw new AccessDeniedException("The award event deadline has passed");
        }
    }

    private boolean isOpen(AwardEvent awardEvent) {
        return !awardEvent.isClosedAt(clock.instant());
    }
}

package com.example.esgaward.proposal;

import java.time.Instant;

import com.example.esgaward.user.UserDto;

public record ProposalSummaryDto(
        Long id,
        Long awardEventId,
        String awardEventName,
        String title,
        UserDto leader,
        int memberCount,
        int fileCount,
        boolean editable,
        Instant createdAt,
        Instant updatedAt) {

    static ProposalSummaryDto from(Proposal proposal, boolean editable) {
        return new ProposalSummaryDto(proposal.getId(), proposal.getAwardEvent().getId(),
                proposal.getAwardEvent().getName(), proposal.getTitle(), UserDto.from(proposal.getLeader()),
                proposal.getMembers().size(), proposal.getFiles().size(), editable,
                proposal.getCreatedAt(), proposal.getUpdatedAt());
    }
}

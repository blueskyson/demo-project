package com.example.esgaward.dto;

import java.time.Instant;

import com.example.esgaward.entity.Proposal;

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

    public static ProposalSummaryDto from(Proposal proposal, boolean editable) {
        return new ProposalSummaryDto(proposal.getId(), proposal.getAwardEvent().getId(),
                proposal.getAwardEvent().getName(), proposal.getTitle(), UserDto.from(proposal.getLeader()),
                proposal.getMembers().size(), proposal.getFiles().size(), editable,
                proposal.getCreatedAt(), proposal.getUpdatedAt());
    }
}

package com.example.esgaward.dto;

import java.time.Instant;
import java.util.List;

import com.example.esgaward.entity.Proposal;

public record ProposalDetailDto(
        Long id,
        AwardEventDto awardEvent,
        String title,
        String description,
        UserDto leader,
        List<UserDto> members,
        List<ProposalFileDto> files,
        /** Whether the current user may edit this proposal, its members and its files. */
        boolean editable,
        Instant createdAt,
        Instant updatedAt) {

    public static ProposalDetailDto from(Proposal proposal, boolean editable, Instant now) {
        return new ProposalDetailDto(proposal.getId(), AwardEventDto.from(proposal.getAwardEvent(), now),
                proposal.getTitle(), proposal.getDescription(), UserDto.from(proposal.getLeader()),
                proposal.getMembers().stream().map(m -> UserDto.from(m.getUser())).toList(),
                proposal.getFiles().stream().map(ProposalFileDto::from).toList(),
                editable, proposal.getCreatedAt(), proposal.getUpdatedAt());
    }
}

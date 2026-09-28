package com.example.esgaward.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param leaderId only admins may set this; normal users always become the leader themselves.
 */
public record CreateProposalRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        UUID leaderId) {
}

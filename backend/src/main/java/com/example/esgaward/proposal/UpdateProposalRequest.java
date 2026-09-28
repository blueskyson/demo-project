package com.example.esgaward.proposal;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param leaderId new leader; {@code null} keeps the current one. Only admins may change it.
 */
public record UpdateProposalRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        UUID leaderId) {
}

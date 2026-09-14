package com.example.demobackend.document.dto;

import jakarta.validation.constraints.NotBlank;

public record ShareDocumentRequest(
        @NotBlank String targetUserId,
        @NotBlank String relation) {
}

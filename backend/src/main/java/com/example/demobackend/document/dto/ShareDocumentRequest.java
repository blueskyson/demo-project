package com.example.demobackend.document.dto;

import com.example.demobackend.authorization.FgaRelation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ShareDocumentRequest(
        @NotBlank String targetUserId,
        @NotNull FgaRelation relation) {
}

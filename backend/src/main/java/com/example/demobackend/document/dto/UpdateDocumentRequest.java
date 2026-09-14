package com.example.demobackend.document.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateDocumentRequest(
        @NotBlank String title,
        String content) {
}

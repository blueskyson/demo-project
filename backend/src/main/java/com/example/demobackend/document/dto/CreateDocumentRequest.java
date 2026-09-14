package com.example.demobackend.document.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDocumentRequest(
        @NotBlank String title,
        String content) {
}

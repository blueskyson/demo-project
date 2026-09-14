package com.example.demobackend.document.dto;

import java.time.Instant;
import java.util.UUID;

import com.example.demobackend.document.Document;

public record DocumentResponse(
        UUID id,
        String title,
        String content,
        String ownerId,
        Instant createdAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getContent(),
                document.getOwnerId(),
                document.getCreatedAt());
    }
}

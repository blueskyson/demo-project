package com.example.esgaward.dto;

import java.time.Instant;

import com.example.esgaward.entity.ProposalFile;

public record ProposalFileDto(
        Long id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        UserDto uploadedBy,
        Instant uploadedAt) {

    public static ProposalFileDto from(ProposalFile file) {
        return new ProposalFileDto(file.getId(), file.getOriginalFilename(), file.getContentType(),
                file.getSizeBytes(), UserDto.from(file.getUploadedBy()), file.getUploadedAt());
    }
}

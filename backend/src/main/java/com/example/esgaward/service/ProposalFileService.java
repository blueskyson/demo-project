package com.example.esgaward.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.esgaward.dto.FileDownload;
import com.example.esgaward.dto.ProposalFileDto;
import com.example.esgaward.entity.Proposal;
import com.example.esgaward.entity.ProposalFile;
import com.example.esgaward.entity.User;
import com.example.esgaward.exception.NotFoundException;
import com.example.esgaward.repository.ProposalFileRepository;
import com.example.esgaward.security.CurrentUserService;
import com.example.esgaward.security.Permission;
import com.example.esgaward.security.RequirePermission;
import com.example.esgaward.security.ResourceId;
import com.example.esgaward.storage.FileStorage;

@Service
public class ProposalFileService {

    private final ProposalService proposalService;
    private final ProposalFileRepository proposalFileRepository;
    private final CurrentUserService currentUserService;
    private final FileStorage fileStorage;
    private final Clock clock;

    public ProposalFileService(ProposalService proposalService, ProposalFileRepository proposalFileRepository,
            CurrentUserService currentUserService, FileStorage fileStorage, Clock clock) {
        this.proposalService = proposalService;
        this.proposalFileRepository = proposalFileRepository;
        this.currentUserService = currentUserService;
        this.fileStorage = fileStorage;
        this.clock = clock;
    }

    @Transactional
    @RequirePermission(Permission.PROPOSAL_FILE_UPLOAD)
    public ProposalFileDto upload(@ResourceId Long proposalId, MultipartFile upload) {
        if (upload.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        User user = currentUserService.currentUser();
        Proposal proposal = proposalService.find(proposalId);

        String key;
        try (InputStream content = upload.getInputStream()) {
            key = fileStorage.store(content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload", e);
        }
        String filename = StringUtils.cleanPath(
                upload.getOriginalFilename() != null ? upload.getOriginalFilename() : "file");
        Instant now = clock.instant();
        ProposalFile file = proposalFileRepository.save(new ProposalFile(proposal, StringUtils.getFilename(filename),
                upload.getContentType(), upload.getSize(), key, user, now));
        proposal.addFile(file, now);
        return ProposalFileDto.from(file);
    }

    /** Returns the file metadata and its content for download. */
    @Transactional
    @RequirePermission(Permission.PROPOSAL_FILE_READ)
    public FileDownload download(@ResourceId Long proposalId, Long fileId) {
        ProposalFile file = findFile(proposalService.find(proposalId), fileId);
        return new FileDownload(ProposalFileDto.from(file), fileStorage.load(file.getStorageKey()));
    }

    @Transactional
    @RequirePermission(Permission.PROPOSAL_FILE_DELETE)
    public void delete(@ResourceId Long proposalId, Long fileId) {
        Proposal proposal = proposalService.find(proposalId);
        ProposalFile file = findFile(proposal, fileId);

        proposal.removeFile(file, clock.instant());
        fileStorage.deleteAfterCommit(file.getStorageKey());
    }

    private static ProposalFile findFile(Proposal proposal, Long fileId) {
        return proposal.getFiles().stream()
                .filter(f -> f.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("File " + fileId + " not found in proposal " + proposal.getId()));
    }
}

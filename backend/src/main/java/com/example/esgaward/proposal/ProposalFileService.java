package com.example.esgaward.proposal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.esgaward.common.NotFoundException;
import com.example.esgaward.security.AccessPolicy;
import com.example.esgaward.storage.FileStorage;
import com.example.esgaward.user.CurrentUserService;
import com.example.esgaward.user.User;

@Service
public class ProposalFileService {

    private final ProposalService proposalService;
    private final ProposalFileRepository proposalFileRepository;
    private final CurrentUserService currentUserService;
    private final AccessPolicy accessPolicy;
    private final FileStorage fileStorage;
    private final Clock clock;

    public ProposalFileService(ProposalService proposalService, ProposalFileRepository proposalFileRepository,
            CurrentUserService currentUserService, AccessPolicy accessPolicy, FileStorage fileStorage, Clock clock) {
        this.proposalService = proposalService;
        this.proposalFileRepository = proposalFileRepository;
        this.currentUserService = currentUserService;
        this.accessPolicy = accessPolicy;
        this.fileStorage = fileStorage;
        this.clock = clock;
    }

    @Transactional
    public ProposalFileDto upload(Long proposalId, MultipartFile upload) {
        User user = currentUserService.currentUser();
        Proposal proposal = proposalService.find(proposalId);
        accessPolicy.checkEdit(user, proposal);
        if (upload.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

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
    public Download download(Long proposalId, Long fileId) {
        User user = currentUserService.currentUser();
        Proposal proposal = proposalService.find(proposalId);
        accessPolicy.checkView(user, proposal);
        ProposalFile file = findFile(proposal, fileId);
        return new Download(ProposalFileDto.from(file), fileStorage.load(file.getStorageKey()));
    }

    @Transactional
    public void delete(Long proposalId, Long fileId) {
        User user = currentUserService.currentUser();
        Proposal proposal = proposalService.find(proposalId);
        accessPolicy.checkEdit(user, proposal);
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

    public record Download(ProposalFileDto file, Resource content) {
    }
}

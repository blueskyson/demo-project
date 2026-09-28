package com.example.esgaward.proposal;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/proposals/{proposalId}/files")
public class ProposalFileController {

    private final ProposalFileService proposalFileService;

    public ProposalFileController(ProposalFileService proposalFileService) {
        this.proposalFileService = proposalFileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalFileDto upload(@PathVariable Long proposalId, @RequestParam("file") MultipartFile file) {
        return proposalFileService.upload(proposalId, file);
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<Resource> download(@PathVariable Long proposalId, @PathVariable Long fileId) {
        ProposalFileService.Download download = proposalFileService.download(proposalId, fileId);
        ProposalFileDto file = download.file();
        MediaType contentType = file.contentType() != null
                ? MediaType.parseMediaType(file.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(download.content());
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long proposalId, @PathVariable Long fileId) {
        proposalFileService.delete(proposalId, fileId);
    }
}

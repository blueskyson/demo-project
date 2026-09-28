package com.example.demobackend.document;

import java.util.List;
import java.util.UUID;

import com.example.demobackend.audit.annotation.Audited;
import com.example.demobackend.audit.annotation.NoAudit;
import com.example.demobackend.document.dto.CreateDocumentRequest;
import com.example.demobackend.document.dto.DocumentResponse;
import com.example.demobackend.document.dto.UpdateDocumentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Audited(action = "DOCUMENT_CREATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateDocumentRequest request) {
        return documentService.createDocument(jwt.getSubject(), request);
    }

    @NoAudit(reason = "lists only the caller's own documents")
    @GetMapping
    public List<DocumentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return documentService.listOwnDocuments(jwt.getSubject());
    }

    @Audited(action = "DOCUMENT_READ")
    @GetMapping("/{id}")
    public DocumentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return documentService.getDocument(jwt.getSubject(), id);
    }

    @Audited(action = "DOCUMENT_UPDATE")
    @PutMapping("/{id}")
    public DocumentResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentRequest request) {
        return documentService.updateDocument(jwt.getSubject(), id, request);
    }

    @Audited(action = "DOCUMENT_DELETE")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        documentService.deleteDocument(jwt.getSubject(), id);
    }
}

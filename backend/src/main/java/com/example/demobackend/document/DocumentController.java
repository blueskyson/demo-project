package com.example.demobackend.document;

import java.util.List;
import java.util.UUID;

import com.example.demobackend.authorization.FgaCheck;
import com.example.demobackend.authorization.FgaObjectType;
import com.example.demobackend.authorization.FgaRelation;
import com.example.demobackend.authorization.NoFgaCheck;
import com.example.demobackend.document.dto.CreateDocumentRequest;
import com.example.demobackend.document.dto.DocumentResponse;
import com.example.demobackend.document.dto.ShareDocumentRequest;
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

    @NoFgaCheck(reason = "document doesn't exist yet; creator becomes owner in DocumentService#createDocument")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateDocumentRequest request) {
        return documentService.createDocument(jwt.getSubject(), request);
    }

    @NoFgaCheck(reason = "filtered via listObjectIds in DocumentService#listVisibleDocuments, not a single-object check")
    @GetMapping
    public List<DocumentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return documentService.listVisibleDocuments(jwt.getSubject());
    }

    @GetMapping("/{id}")
    @FgaCheck(objectType = FgaObjectType.DOCUMENT, relation = FgaRelation.VIEWER, idParam = "id")
    public DocumentResponse get(@PathVariable UUID id) {
        return documentService.getDocument(id);
    }

    /** No {@code @FgaCheck} here — {@link DocumentService#updateDocument} carries it instead. */
    @NoFgaCheck(reason = "enforced in DocumentService#updateDocument")
    @PutMapping("/{id}")
    public DocumentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest request) {
        return documentService.updateDocument(id, request);
    }

    /** No {@code @FgaCheck} here — {@link DocumentService#deleteDocument} carries it instead. */
    @NoFgaCheck(reason = "enforced in DocumentService#deleteDocument")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        documentService.deleteDocument(id);
    }

    /** No {@code @FgaCheck} here — {@link DocumentService#shareDocument} carries it instead. */
    @NoFgaCheck(reason = "enforced in DocumentService#shareDocument")
    @PostMapping("/{id}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void share(@PathVariable UUID id, @Valid @RequestBody ShareDocumentRequest request) {
        documentService.shareDocument(id, request);
    }
}

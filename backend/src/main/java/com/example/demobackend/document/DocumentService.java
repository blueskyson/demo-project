package com.example.demobackend.document;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import com.example.demobackend.authorization.AuthorizationService;
import com.example.demobackend.authorization.FgaCheck;
import com.example.demobackend.authorization.FgaObjectType;
import com.example.demobackend.authorization.FgaRelation;
import com.example.demobackend.authorization.NoFgaCheck;
import com.example.demobackend.document.dto.CreateDocumentRequest;
import com.example.demobackend.document.dto.DocumentResponse;
import com.example.demobackend.document.dto.ShareDocumentRequest;
import com.example.demobackend.document.dto.UpdateDocumentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private static final FgaObjectType DOCUMENT_TYPE = FgaObjectType.DOCUMENT;
    private static final Set<FgaRelation> SHAREABLE_RELATIONS = Set.of(FgaRelation.VIEWER, FgaRelation.EDITOR);

    private final DocumentRepository documentRepository;
    private final AuthorizationService authorizationService;

    public DocumentService(DocumentRepository documentRepository, AuthorizationService authorizationService) {
        this.documentRepository = documentRepository;
        this.authorizationService = authorizationService;
    }

    @NoFgaCheck(reason = "document doesn't exist yet; creator becomes owner via writeTuple, not a check")
    @Transactional
    public DocumentResponse createDocument(String userId, CreateDocumentRequest request) {
        Document document = documentRepository.save(
                new Document(request.title(), request.content(), userId));
        authorizationService.writeTuple(userId, FgaRelation.OWNER, DOCUMENT_TYPE, document.getId().toString());
        return DocumentResponse.from(document);
    }

    /**
     * Service-layer authorization: the visible set isn't a single object's permission
     * check, it's a query shaped by OpenFGA's ListObjects — there's no annotation that
     * expresses "filter this collection to what the caller can see".
     */
    @NoFgaCheck(reason = "filters via listObjectIds, not a single-object check")
    public List<DocumentResponse> listVisibleDocuments(String userId) {
        List<UUID> visibleIds = authorizationService.listObjectIds(userId, FgaRelation.VIEWER, DOCUMENT_TYPE)
                .stream()
                .map(UUID::fromString)
                .toList();
        return documentRepository.findAllById(visibleIds).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @NoFgaCheck(reason = "enforced in DocumentController#get")
    public DocumentResponse getDocument(UUID id) {
        return DocumentResponse.from(findOrThrow(id));
    }

    /** Only the owner may update — not just any editor — so this checks {@code owner}, not {@code editor}. */
    @FgaCheck(objectType = FgaObjectType.DOCUMENT, relation = FgaRelation.OWNER, idParam = "id")
    @Transactional
    public DocumentResponse updateDocument(UUID id, UpdateDocumentRequest request) {
        Document document = findOrThrow(id);
        document.setTitle(request.title());
        document.setContent(request.content());
        return DocumentResponse.from(document);
    }

    @FgaCheck(objectType = FgaObjectType.DOCUMENT, relation = FgaRelation.OWNER, idParam = "id")
    @Transactional
    public void deleteDocument(UUID id) {
        if (!documentRepository.existsById(id)) {
            throw new NoSuchElementException("Document " + id + " not found");
        }
        documentRepository.deleteById(id);
    }

    /**
     * Service-layer authorization example: the "caller must be owner" half of the rule
     * is a plain relation check, so it's expressed with {@code @FgaCheck} like the
     * Controller-layer examples — the aspect reads the caller off
     * {@code SecurityContextHolder}, so this method doesn't need a requesterId parameter.
     * What can't be expressed declaratively is the rest of the rule — only viewer/editor
     * may be granted (never re-granting owner) — so that half stays as an explicit check
     * in the method body.
     */
    @FgaCheck(objectType = FgaObjectType.DOCUMENT, relation = FgaRelation.OWNER, idParam = "documentId")
    @Transactional
    public void shareDocument(UUID documentId, ShareDocumentRequest request) {
        findOrThrow(documentId);

        if (!SHAREABLE_RELATIONS.contains(request.relation())) {
            throw new IllegalArgumentException("relation must be one of " + SHAREABLE_RELATIONS);
        }

        authorizationService.writeTuple(request.targetUserId(), request.relation(), DOCUMENT_TYPE,
                documentId.toString());
    }

    private Document findOrThrow(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Document " + id + " not found"));
    }
}

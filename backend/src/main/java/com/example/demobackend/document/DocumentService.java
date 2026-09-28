package com.example.demobackend.document;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import com.example.demobackend.document.dto.CreateDocumentRequest;
import com.example.demobackend.document.dto.DocumentResponse;
import com.example.demobackend.document.dto.UpdateDocumentRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Transactional
    public DocumentResponse createDocument(String userId, CreateDocumentRequest request) {
        Document document = documentRepository.save(new Document(request.title(), request.content(), userId));
        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listOwnDocuments(String userId) {
        return documentRepository.findAllByOwnerIdOrderByCreatedAtDesc(userId).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(String userId, UUID id) {
        return DocumentResponse.from(findOwnedOrThrow(userId, id));
    }

    @Transactional
    public DocumentResponse updateDocument(String userId, UUID id, UpdateDocumentRequest request) {
        Document document = findOwnedOrThrow(userId, id);
        document.setTitle(request.title());
        document.setContent(request.content());
        return DocumentResponse.from(document);
    }

    @Transactional
    public void deleteDocument(String userId, UUID id) {
        documentRepository.delete(findOwnedOrThrow(userId, id));
    }

    private Document findOwnedOrThrow(String userId, UUID id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Document " + id + " not found"));
        if (!document.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Only the owner can access document " + id);
        }
        return document;
    }
}

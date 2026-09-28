package com.example.demobackend.document;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);
}

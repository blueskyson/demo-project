package com.example.auditfixture;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Deliberately violates the audit rules; only used by {@code AuditArchitectureFixtureTest}.
 * Lives outside {@code com.example.demobackend} so Spring never scans it.
 */
@Entity
public class UnauditedEntity {

    @Id
    private Long id;
}

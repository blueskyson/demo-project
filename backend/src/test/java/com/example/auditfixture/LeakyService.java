package com.example.auditfixture;

import com.example.demobackend.audit.AuditLogWriter;

/**
 * Deliberately violates the audit rules; only used by {@code AuditArchitectureFixtureTest}.
 * Lives outside {@code com.example.demobackend} so Spring never scans it.
 */
public class LeakyService {

    private final AuditLogWriter writer;

    public LeakyService(AuditLogWriter writer) {
        this.writer = writer;
    }
}

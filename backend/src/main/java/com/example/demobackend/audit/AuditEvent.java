package com.example.demobackend.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of one audited operation. It's fully built on the request thread
 * (no entity references, no ThreadLocal lookups) so the async writer can persist it safely.
 */
public record AuditEvent(
        UUID id,
        Instant occurredAt,
        String actorId,
        String actorName,
        String action,
        AuditOutcome outcome,
        String errorType,
        String httpMethod,
        String route,
        String path,
        String clientIp,
        String requestId,
        Long durationMs,
        List<EntityChange> changes) {

    static final String UNATTRIBUTED_ACTION = "ENTITY_CHANGE";

    public enum AuditOutcome { SUCCESS, DENIED, FAILURE }

    public enum Operation { INSERT, UPDATE, DELETE }

    public record EntityChange(String entityType, String entityId, Operation operation, List<FieldChange> fields) {
    }

    public record FieldChange(String field, String oldValue, String newValue) {
    }

    /** Entity changes committed outside any {@code @Audited} endpoint, e.g. by a scheduled job. */
    static AuditEvent unattributed(List<EntityChange> changes) {
        AuditActor actor = AuditActor.current();
        RequestInfo request = RequestInfo.current();
        return new AuditEvent(UUID.randomUUID(), Instant.now(), actor.id(), actor.name(), UNATTRIBUTED_ACTION,
                AuditOutcome.SUCCESS, null, request.method(), request.route(), request.path(), request.clientIp(), request.requestId(), null,
                List.copyOf(changes));
    }
}

package com.example.demobackend.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.demobackend.audit.AuditEvent.AuditOutcome;
import com.example.demobackend.audit.AuditEvent.EntityChange;
import org.springframework.security.access.AccessDeniedException;

/**
 * The {@code @Audited} operation currently running on this thread. Entity changes committed
 * while it's open are attached to it, so one audit entry holds both "who did what" and
 * "which fields changed".
 */
final class AuditScope {

    private static final ThreadLocal<AuditScope> CURRENT = new ThreadLocal<>();

    private final UUID id = UUID.randomUUID();
    private final Instant startedAt = Instant.now();
    private final long startNanos = System.nanoTime();
    private final String action;
    private final AuditActor actor = AuditActor.current();
    private final RequestInfo request = RequestInfo.current();
    private final List<EntityChange> changes = new ArrayList<>();

    private AuditOutcome outcome = AuditOutcome.SUCCESS;
    private String errorType;
    private long durationMs;
    private boolean finished;

    AuditScope(String action) {
        this.action = action;
    }

    static AuditScope current() {
        return CURRENT.get();
    }

    static void bind(AuditScope scope) {
        CURRENT.set(scope);
    }

    static void unbind() {
        CURRENT.remove();
    }

    boolean isFinished() {
        return finished;
    }

    void addChanges(List<EntityChange> committed) {
        changes.addAll(committed);
    }

    /** Called when the audited method returns or throws. */
    void complete(Throwable failure) {
        durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (failure != null) {
            outcome = failure instanceof AccessDeniedException ? AuditOutcome.DENIED : AuditOutcome.FAILURE;
            errorType = failure.getClass().getName();
        }
    }

    /** The surrounding transaction rolled back after the method returned normally. */
    void rolledBack() {
        if (outcome == AuditOutcome.SUCCESS) {
            outcome = AuditOutcome.FAILURE;
            errorType = "TransactionRolledBack";
        }
    }

    AuditEvent toEvent() {
        finished = true;
        return new AuditEvent(id, startedAt, actor.id(), actor.name(), action, outcome, errorType,
                request.method(), request.route(), request.path(), request.clientIp(), request.requestId(),
                durationMs, List.copyOf(changes));
    }
}

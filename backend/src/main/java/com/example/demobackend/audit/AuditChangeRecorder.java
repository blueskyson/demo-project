package com.example.demobackend.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.demobackend.audit.AuditEvent.EntityChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Buffers entity changes per transaction and releases them only after commit, so rolled-back
 * changes never show up in the audit log. Committed changes go to the {@link AuditScope}
 * that was open when they were flushed, or become an unattributed event if there was none.
 */
@Component
class AuditChangeRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditChangeRecorder.class);

    private final AuditPublisher publisher;

    AuditChangeRecorder(AuditPublisher publisher) {
        this.publisher = publisher;
    }

    void record(EntityChange change) {
        AuditScope scope = AuditScope.current();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deliver(scope, List.of(change));
            return;
        }
        PendingChanges pending = (PendingChanges) TransactionSynchronizationManager.getResource(this);
        if (pending == null) {
            pending = new PendingChanges();
            TransactionSynchronizationManager.bindResource(this, pending);
            TransactionSynchronizationManager.registerSynchronization(pending);
        }
        pending.add(scope, change);
    }

    private void deliver(AuditScope scope, List<EntityChange> changes) {
        if (scope != null && !scope.isFinished()) {
            scope.addChanges(changes);
        } else {
            publisher.publish(AuditEvent.unattributed(changes));
        }
    }

    private final class PendingChanges implements TransactionSynchronization {

        // Keyed by scope (null = no scope); LinkedHashMap keeps flush order.
        private final Map<AuditScope, List<EntityChange>> changesByScope = new LinkedHashMap<>();

        void add(AuditScope scope, EntityChange change) {
            changesByScope.computeIfAbsent(scope, key -> new ArrayList<>()).add(change);
        }

        @Override
        public void suspend() {
            TransactionSynchronizationManager.unbindResource(AuditChangeRecorder.this);
        }

        @Override
        public void resume() {
            TransactionSynchronizationManager.bindResource(AuditChangeRecorder.this, this);
        }

        @Override
        public void afterCommit() {
            try {
                changesByScope.forEach(AuditChangeRecorder.this::deliver);
            } catch (RuntimeException ex) {
                log.warn("Could not deliver committed entity changes to the audit log", ex);
            }
        }

        @Override
        public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(AuditChangeRecorder.this);
        }
    }
}

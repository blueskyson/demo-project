package com.example.demobackend.audit;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.example.demobackend.audit.AuditEvent.EntityChange;
import com.example.demobackend.audit.AuditEvent.FieldChange;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persists audit events with plain JDBC (not JPA), so writing the audit log can never
 * trigger Hibernate's entity listeners or join a business transaction.
 */
@Component
public class AuditLogWriter {

    private static final String INSERT_LOG = """
            insert into audit_log (id, occurred_at, actor_id, actor_name, action, outcome, error_type,
                                   http_method, route, path, client_ip, request_id, duration_ms)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_CHANGE = """
            insert into audit_log_change (audit_log_id, entity_type, entity_id, operation, field_name, old_value, new_value)
            values (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    AuditLogWriter(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public void write(AuditEvent event) {
        List<Object[]> changeRows = new ArrayList<>();
        for (EntityChange change : event.changes()) {
            for (FieldChange field : change.fields()) {
                changeRows.add(new Object[] { event.id(), change.entityType(), change.entityId(),
                        change.operation().name(), field.field(), field.oldValue(), field.newValue() });
            }
        }

        transaction.executeWithoutResult(status -> {
            jdbc.update(INSERT_LOG, event.id(), utc(event.occurredAt()), event.actorId(), event.actorName(),
                    event.action(), event.outcome().name(), event.errorType(), event.httpMethod(), event.route(),
                    event.path(), event.clientIp(), event.requestId(),
                    event.durationMs());
            if (!changeRows.isEmpty()) {
                jdbc.batchUpdate(INSERT_CHANGE, changeRows);
            }
        });
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}

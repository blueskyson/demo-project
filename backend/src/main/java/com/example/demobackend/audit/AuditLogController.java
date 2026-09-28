package com.example.demobackend.audit;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.demobackend.audit.annotation.Audited;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only (see SecurityConfig) view of the audit log, newest first. Filtering by entity
 * finds every operation that changed it, whichever endpoint (or job) did so.
 */
@RestController
@RequestMapping("/api/audit-logs")
class AuditLogController {

    record AuditLogView(UUID id, OffsetDateTime occurredAt, String actorId, String actorName, String action,
            String outcome, String errorType, String httpMethod, String route, String path, String clientIp, String requestId, Long durationMs, List<ChangeView> changes) {
    }

    record ChangeView(String entityType, String entityId, String operation, String field, String oldValue,
            String newValue) {
    }

    private final JdbcClient jdbc;

    AuditLogController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Audited(action = "AUDIT_LOG_READ")
    @GetMapping
    List<AuditLogView> list(@RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String actorId,
            @RequestParam(defaultValue = "50") int limit) {
        StringBuilder sql = new StringBuilder("select * from audit_log l where 1 = 1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (entityType != null || entityId != null) {
            sql.append(" and exists (select 1 from audit_log_change c where c.audit_log_id = l.id");
            if (entityType != null) {
                sql.append(" and c.entity_type = :entityType");
                params.put("entityType", entityType);
            }
            if (entityId != null) {
                sql.append(" and c.entity_id = :entityId");
                params.put("entityId", entityId);
            }
            sql.append(")");
        }
        if (actorId != null) {
            sql.append(" and l.actor_id = :actorId");
            params.put("actorId", actorId);
        }
        sql.append(" order by l.occurred_at desc limit :limit");
        params.put("limit", Math.min(Math.max(limit, 1), 500));

        List<AuditLogView> logs = jdbc.sql(sql.toString())
                .params(params)
                .query((rs, row) -> new AuditLogView(rs.getObject("id", UUID.class),
                        rs.getObject("occurred_at", OffsetDateTime.class), rs.getString("actor_id"),
                        rs.getString("actor_name"), rs.getString("action"), rs.getString("outcome"),
                        rs.getString("error_type"), rs.getString("http_method"), rs.getString("route"),
                        rs.getString("path"), rs.getString("client_ip"),
                        rs.getString("request_id"), rs.getObject("duration_ms", Long.class), new ArrayList<>()))
                .list();
        if (logs.isEmpty()) {
            return logs;
        }

        Map<UUID, AuditLogView> byId = new LinkedHashMap<>();
        logs.forEach(log -> byId.put(log.id(), log));
        jdbc.sql("select * from audit_log_change where audit_log_id in (:ids) order by id")
                .param("ids", byId.keySet())
                .query(rs -> {
                    byId.get(rs.getObject("audit_log_id", UUID.class)).changes().add(new ChangeView(
                            rs.getString("entity_type"), rs.getString("entity_id"), rs.getString("operation"),
                            rs.getString("field_name"), rs.getString("old_value"), rs.getString("new_value")));
                });
        return logs;
    }
}

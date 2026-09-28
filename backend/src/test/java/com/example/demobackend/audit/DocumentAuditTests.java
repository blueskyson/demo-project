package com.example.demobackend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.example.demobackend.document.Document;
import com.example.demobackend.document.DocumentRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentAuditTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DocumentRepository documents;

    @Autowired
    TransactionTemplate transaction;

    @Test
    void createUpdateDeleteRecordFieldChangesWithoutTimestamps() throws Exception {
        String id = createDocument(user("alice"), "Draft", "v1");

        mvc.perform(put("/api/documents/{id}", id).with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Final\",\"content\":\"v1\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/documents/{id}", id).with(user("alice")))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> logs = awaitLogs(id, 3);
        assertThat(logs).extracting(log -> log.get("action"))
                .containsExactly("DOCUMENT_CREATE", "DOCUMENT_UPDATE", "DOCUMENT_DELETE");
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.get("outcome")).isEqualTo("SUCCESS");
            assertThat(log.get("actor_id")).isEqualTo("alice-id");
            assertThat(log.get("actor_name")).isEqualTo("alice");
        });

        assertThat(changes(logs.get(0))).containsExactlyInAnyOrder(
                "INSERT content null -> v1", "INSERT ownerId null -> alice-id", "INSERT title null -> Draft");
        // content didn't change and updatedAt is ignored, so only the title shows up.
        assertThat(changes(logs.get(1))).containsExactly("UPDATE title Draft -> Final");
        assertThat(changes(logs.get(2))).containsExactlyInAnyOrder(
                "DELETE content v1 -> null", "DELETE ownerId alice-id -> null", "DELETE title Final -> null");
    }

    @Test
    void deniedUpdateIsAuditedWithoutChanges() throws Exception {
        String id = createDocument(user("alice"), "Alice's", "secret");

        mvc.perform(put("/api/documents/{id}", id).with(user("bob"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hacked\",\"content\":\"\"}"))
                .andExpect(status().isForbidden());

        // Nothing changed, so the target is identified by the request path instead of entity changes.
        Map<String, Object> denied = await().atMost(Duration.ofSeconds(5)).until(
                () -> jdbc.sql("select * from audit_log where path = ? and outcome = 'DENIED'")
                        .param("/api/documents/" + id).query().listOfRows(),
                rows -> !rows.isEmpty()).get(0);
        assertThat(denied.get("action")).isEqualTo("DOCUMENT_UPDATE");
        assertThat(denied.get("route")).isEqualTo("/api/documents/{id}");
        assertThat(denied.get("outcome")).isEqualTo("DENIED");
        assertThat(denied.get("actor_name")).isEqualTo("bob");
        assertThat(denied.get("http_method")).isEqualTo("PUT");
        assertThat(changes(denied)).isEmpty();
    }

    @Test
    void changesOutsideAnEndpointAreAuditedAsUnattributed() {
        Document document = transaction.execute(status -> documents.save(new Document("Imported", null, "job")));

        List<Map<String, Object>> logs = awaitLogs(document.getId().toString(), 1);
        assertThat(logs.get(0).get("action")).isEqualTo("ENTITY_CHANGE");
        assertThat(logs.get(0).get("actor_name")).isEqualTo("system");
        assertThat(changes(logs.get(0))).containsExactlyInAnyOrder(
                "INSERT ownerId null -> job", "INSERT title null -> Imported");
    }

    @Test
    void oneOperationChangingSeveralEntitiesIsOneEntryListingAllOfThem() {
        List<Document> saved = transaction.execute(status -> documents.saveAll(List.of(
                new Document("A", null, "job"), new Document("B", null, "job"))));
        String first = saved.get(0).getId().toString();
        String second = saved.get(1).getId().toString();

        Map<String, Object> log = awaitLogs(first, 1).get(0);
        assertThat(logsFor(second)).extracting(row -> row.get("id")).containsExactly(log.get("id"));
        assertThat(jdbc.sql("select distinct entity_id from audit_log_change where audit_log_id = ?")
                .param(log.get("id")).query(String.class).list())
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void rolledBackChangesAreNotAudited() {
        Document document = transaction.execute(status -> {
            Document saved = documents.saveAndFlush(new Document("Rolled back", null, "job"));
            status.setRollbackOnly();
            return saved;
        });

        // Prove the audit pipeline is idle by waiting for a later event, then check nothing was written.
        Document marker = transaction.execute(status -> documents.save(new Document("marker", null, "job")));
        awaitLogs(marker.getId().toString(), 1);
        assertThat(logsFor(document.getId().toString())).isEmpty();
    }

    @Test
    void onlyAdminsCanReadTheAuditLog() throws Exception {
        String id = createDocument(user("alice"), "Visible", null);
        awaitLogs(id, 1);

        mvc.perform(get("/api/audit-logs").param("entityType", "Document").param("entityId", id)
                        .with(user("alice")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit-logs").param("entityType", "Document").param("entityId", id)
                        .with(user("root").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("DOCUMENT_CREATE"))
                .andExpect(jsonPath("$[0].changes[?(@.field == 'title')].newValue").value("Visible"));
    }

    private String createDocument(JwtRequestPostProcessor user, String title, String content) throws Exception {
        String body = mvc.perform(post("/api/documents").with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":" + (content == null ? "null" : "\"" + content + "\"") + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static JwtRequestPostProcessor user(String username) {
        return jwt().jwt(token -> token.subject(username + "-id").claim("preferred_username", username));
    }

    private List<Map<String, Object>> awaitLogs(String entityId, int count) {
        return await().atMost(Duration.ofSeconds(5))
                .until(() -> logsFor(entityId), logs -> logs.size() >= count);
    }

    /** Every audit entry that changed the given entity. */
    private List<Map<String, Object>> logsFor(String entityId) {
        return jdbc.sql("""
                        select * from audit_log l
                        where exists (select 1 from audit_log_change c where c.audit_log_id = l.id and c.entity_id = ?)
                        order by l.occurred_at
                        """)
                .param(entityId)
                .query().listOfRows();
    }

    private List<String> changes(Map<String, Object> log) {
        return jdbc.sql("select * from audit_log_change where audit_log_id = ? order by id")
                .param(log.get("id"))
                .query((rs, row) -> rs.getString("operation") + " " + rs.getString("field_name") + " "
                        + rs.getString("old_value") + " -> " + rs.getString("new_value"))
                .list();
    }
}

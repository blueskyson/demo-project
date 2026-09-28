package com.example.esgaward;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import org.testcontainers.openfga.OpenFGAContainer;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProposalAuthorizationTests {

    /** A real OpenFGA, so these tests also exercise openfga/model.fga end to end. */
    static final OpenFGAContainer OPENFGA = new OpenFGAContainer("openfga/openfga:v1.21.0");

    static {
        OPENFGA.start();
    }

    @DynamicPropertySource
    static void openFgaProperties(DynamicPropertyRegistry registry) {
        registry.add("openfga.api-url", OPENFGA::getHttpEndpoint);
    }

    @Autowired
    private MockMvc mvc;

    private RequestPostProcessor admin;
    private RequestPostProcessor alice;
    private RequestPostProcessor bob;
    private UUID bobId;

    @BeforeEach
    void setUp() throws Exception {
        // Fresh users per test so tests don't depend on each other's data.
        admin = user("ADMIN");
        alice = user("NORMAL_USER");
        bobId = UUID.randomUUID();
        bob = user(bobId, "NORMAL_USER");
        for (RequestPostProcessor u : new RequestPostProcessor[] {admin, alice, bob}) {
            mvc.perform(get("/api/users/me").with(u)).andExpect(status().isOk());
        }
    }

    @Test
    void onlyAdminCanManageAwardEvents() throws Exception {
        mvc.perform(post("/api/award-events").with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(Instant.now().plus(1, ChronoUnit.DAYS))))
                .andExpect(status().isForbidden());

        long eventId = createEvent(Instant.now().plus(1, ChronoUnit.DAYS));
        mvc.perform(get("/api/award-events/" + eventId).with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(false));
        mvc.perform(delete("/api/award-events/" + eventId).with(alice)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/award-events/" + eventId).with(admin)).andExpect(status().isNoContent());
    }

    @Test
    void leaderCanEditProposalBeforeDeadline() throws Exception {
        long eventId = createEvent(Instant.now().plus(1, ChronoUnit.DAYS));
        long proposalId = createProposal(alice, eventId);

        mvc.perform(put("/api/proposals/" + proposalId).with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated\",\"description\":\"d\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"))
                .andExpect(jsonPath("$.editable").value(true));

        mvc.perform(post("/api/proposals/" + proposalId + "/members").with(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + bobId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].id").value(bobId.toString()));

        MockMultipartFile file = new MockMultipartFile("file", "plan.txt", "text/plain", "hello".getBytes());
        String uploaded = mvc.perform(multipart("/api/proposals/" + proposalId + "/files").file(file).with(alice))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fileId = ((Number) JsonPath.read(uploaded, "$.id")).longValue();

        // Members can view and download, but not edit.
        mvc.perform(get("/api/proposals/" + proposalId).with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false));
        mvc.perform(get("/api/proposals/" + proposalId + "/files/" + fileId + "/content").with(bob))
                .andExpect(status().isOk())
                .andExpect(content().string("hello"));
        mvc.perform(put("/api/proposals/" + proposalId).with(bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/proposals/" + proposalId + "/files/" + fileId).with(bob))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/proposals/" + proposalId + "/files/" + fileId).with(alice))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/proposals/" + proposalId).with(alice)).andExpect(status().isNoContent());
    }

    @Test
    void nonMembersCannotSeeOrEditProposal() throws Exception {
        long eventId = createEvent(Instant.now().plus(1, ChronoUnit.DAYS));
        long proposalId = createProposal(alice, eventId);

        mvc.perform(get("/api/proposals/" + proposalId).with(bob)).andExpect(status().isForbidden());
        mvc.perform(put("/api/proposals/" + proposalId).with(bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/proposals").param("awardEventId", String.valueOf(eventId)).with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/proposals").param("awardEventId", String.valueOf(eventId)).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void leaderCannotEditAfterDeadlineButAdminCan() throws Exception {
        long eventId = createEvent(Instant.now().plus(1, ChronoUnit.DAYS));
        long proposalId = createProposal(alice, eventId);

        // Admin moves the deadline into the past.
        mvc.perform(put("/api/award-events/" + eventId).with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(Instant.now().minus(1, ChronoUnit.MINUTES))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true));

        mvc.perform(get("/api/proposals/" + proposalId).with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false));
        mvc.perform(put("/api/proposals/" + proposalId).with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Late\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("The award event deadline has passed"));
        mvc.perform(post("/api/proposals/" + proposalId + "/members").with(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + bobId + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/award-events/" + eventId + "/proposals").with(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"New\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/proposals/" + proposalId).with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Fixed by admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Fixed by admin"));
    }

    @Test
    void onlyAdminCanChangeLeader() throws Exception {
        long eventId = createEvent(Instant.now().plus(1, ChronoUnit.DAYS));
        long proposalId = createProposal(alice, eventId);
        String reassign = "{\"title\":\"t\",\"leaderId\":\"" + bobId + "\"}";

        mvc.perform(put("/api/proposals/" + proposalId).with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content(reassign))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/proposals/" + proposalId).with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(reassign))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leader.id").value(bobId.toString()));

        // Alice is no longer the leader (nor a member), so she loses access.
        mvc.perform(get("/api/proposals/" + proposalId).with(alice)).andExpect(status().isForbidden());
    }

    @Test
    void missingResourceIsNotFound() throws Exception {
        mvc.perform(get("/api/proposals/999999").with(alice)).andExpect(status().isNotFound());
        mvc.perform(put("/api/award-events/999999").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(Instant.now())))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokenWithoutAppRoleIsRejected() throws Exception {
        mvc.perform(get("/api/award-events").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(get("/api/award-events")).andExpect(status().isUnauthorized());
    }

    private long createEvent(Instant deadline) throws Exception {
        String body = mvc.perform(post("/api/award-events").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(deadline)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long createProposal(RequestPostProcessor leader, long eventId) throws Exception {
        String body = mvc.perform(post("/api/award-events/" + eventId + "/proposals").with(leader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Solar roof\",\"description\":\"d\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.editable").value(true))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String eventJson(Instant deadline) {
        return "{\"name\":\"ESG Award 2026\",\"description\":\"desc\",\"deadline\":\"" + deadline + "\"}";
    }

    private static RequestPostProcessor user(String role) {
        return user(UUID.randomUUID(), role);
    }

    private static RequestPostProcessor user(UUID id, String role) {
        return jwt()
                .jwt(j -> j.subject(id.toString()).claim("preferred_username", "user-" + id))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}

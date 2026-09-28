package com.example.demobackend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Auditing must neither slow down nor break the business request. */
@SpringBootTest
@AutoConfigureMockMvc
class AuditResilienceTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuditLogWriter writer;

    @Test
    void failingAuditStoreDoesNotFailTheRequest() throws Exception {
        doThrow(new IllegalStateException("audit DB down")).when(writer).write(any());

        mvc.perform(createDocument()).andExpect(status().isCreated());

        verify(writer, timeout(2000)).write(any());
    }

    @Test
    void slowAuditStoreDoesNotBlockTheRequest() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> release.await(5, TimeUnit.SECONDS)).when(writer).write(any());

        long start = System.nanoTime();
        mvc.perform(createDocument()).andExpect(status().isCreated());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        release.countDown();
        assertThat(elapsedMs).isLessThan(1000); // the writer is still blocked while the response is already done
    }

    private static MockHttpServletRequestBuilder createDocument() {
        return post("/api/documents")
                .with(jwt().jwt(token -> token.subject("carol-id").claim("preferred_username", "carol")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"t\",\"content\":\"c\"}");
    }
}

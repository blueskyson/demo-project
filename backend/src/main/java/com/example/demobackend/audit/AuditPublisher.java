package com.example.demobackend.audit;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

/**
 * Hands audit events to a bounded background pool and returns immediately. The request
 * thread never waits for the database, and a failing or overloaded audit store only costs
 * the audit entry (written to the {@code AUDIT_FALLBACK} log instead), never the request.
 */
@Component
@EnableConfigurationProperties(AuditProperties.class)
class AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);
    /** Route this logger to a separate file/appender in production so lost events can be replayed. */
    private static final Logger fallbackLog = LoggerFactory.getLogger("AUDIT_FALLBACK");

    private final AuditLogWriter writer;
    private final ThreadPoolExecutor executor;
    private final AtomicLong droppedEvents = new AtomicLong();

    AuditPublisher(AuditLogWriter writer, AuditProperties properties) {
        this.writer = writer;
        CustomizableThreadFactory threadFactory = new CustomizableThreadFactory("audit-writer-");
        threadFactory.setDaemon(true);
        this.executor = new ThreadPoolExecutor(
                properties.workerThreads(), properties.workerThreads(), 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()); // full queue -> reject instead of running on the caller
    }

    void publish(AuditEvent event) {
        try {
            CompletableFuture.runAsync(() -> writer.write(event), executor)
                    .whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            fallback(event, failure);
                        }
                    });
        } catch (RejectedExecutionException ex) {
            long dropped = droppedEvents.incrementAndGet();
            log.warn("Audit queue is full; event {} written to fallback log ({} dropped so far)", event.id(), dropped);
            fallback(event, ex);
        } catch (RuntimeException ex) {
            fallback(event, ex);
        }
    }

    long droppedEvents() {
        return droppedEvents.get();
    }

    private void fallback(AuditEvent event, Throwable cause) {
        log.error("Could not write audit event {}", event.id(), cause);
        fallbackLog.error("{}", event);
    }

    /** Drain queued events on shutdown so a normal restart doesn't lose audit entries. */
    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            int lost = executor.shutdownNow().size();
            log.warn("Audit writer didn't finish in time; {} queued events were not written", lost);
        }
    }
}

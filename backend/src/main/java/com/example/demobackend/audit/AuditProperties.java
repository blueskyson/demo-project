package com.example.demobackend.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param workerThreads  threads writing audit events to the database
 * @param queueCapacity  events waiting to be written; beyond this they are dropped, never blocking the caller
 * @param maxValueLength longer field values are truncated in the change log
 */
@ConfigurationProperties("app.audit")
public record AuditProperties(
        @DefaultValue("2") int workerThreads,
        @DefaultValue("10000") int queueCapacity,
        @DefaultValue("2000") int maxValueLength) {
}

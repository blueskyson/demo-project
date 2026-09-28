package com.example.demobackend.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level changes (insert / update / delete) of this JPA entity are recorded in the audit log.
 * Fields annotated with {@link AuditIgnore}, {@code @CreationTimestamp}, {@code @UpdateTimestamp}
 * or {@code @Version} are skipped.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditedEntity {
}

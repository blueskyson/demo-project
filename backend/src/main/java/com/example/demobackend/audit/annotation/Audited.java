package com.example.demobackend.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records one audit log entry for every invocation of the annotated controller method:
 * who called it, the outcome (success / denied / failure), and every field change of
 * {@link AuditedEntity audited entities} committed while it ran.
 * <p>
 * Which resources were affected is not declared here: it's derived from the entity changes
 * Hibernate actually flushed, so an endpoint touching several entities is covered automatically
 * and the annotation can't go stale when the implementation changes.
 * <p>
 * The entry is written asynchronously, so auditing never slows down or breaks the request.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Business action name, e.g. {@code DOCUMENT_UPDATE}. */
    String action();
}

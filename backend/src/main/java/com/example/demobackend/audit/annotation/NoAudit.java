package com.example.demobackend.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explicitly opts a read-only endpoint or an entity out of auditing. The architecture
 * tests require every endpoint and entity to be either audited or carry this annotation,
 * so the decision is always deliberate and reviewable.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoAudit {

    /** Why this doesn't need auditing; must not be blank. */
    String reason();
}

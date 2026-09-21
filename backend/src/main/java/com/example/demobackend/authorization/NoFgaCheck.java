package com.example.demobackend.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explicit opt-out of {@link FgaCheck} on a Controller or Service method. Every public
 * method on a {@code @RestController} or {@code @Service} must carry either {@link FgaCheck}
 * or this annotation — enforced by an ArchUnit test — so that a method added without either
 * fails the build instead of silently shipping unprotected.
 *
 * <p>Use this when a method is intentionally uncovered: it's genuinely public, its
 * authorization is expressed some other way (e.g. {@code ListObjects}-filtered queries), or
 * the check lives on a different layer (Controller vs. Service) for the same operation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NoFgaCheck {

    /** Why this method doesn't need (or doesn't itself carry) an {@link FgaCheck}. */
    String reason();
}

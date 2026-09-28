package com.example.esgaward.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the permission a service method requires. {@link PermissionAspect} checks it before the
 * method runs; {@code ServiceArchitectureTest} fails the build if a public service method lacks it.
 *
 * <p>Like any Spring AOP advice this only applies to calls through the Spring proxy, so calling an
 * annotated method from within the same class skips the check.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    Permission value();
}

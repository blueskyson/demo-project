package com.example.demobackend.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Authorization gate usable on both Controller and Service methods: declares that the
 * current user (read from {@code SecurityContextHolder}, never a method parameter) must
 * hold {@link #relation()} on {@code objectType():idParam()} for the annotated method to
 * run. Enforced by {@link FgaCheckAspect}.
 *
 * <p>Place this at exactly one layer per operation — a simple "does the caller have
 * relation X" gate belongs on the Controller method; a check entangled with other
 * business rules (see {@code DocumentService.shareDocument}) belongs on the Service
 * method instead. Annotating both would run the OpenFGA check twice per request.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FgaCheck {

    /** OpenFGA subject type. */
    FgaUserType userType() default FgaUserType.USER;

    /** OpenFGA relation required. */
    FgaRelation relation();

    /** OpenFGA object type. */
    FgaObjectType objectType();

    /**
     * Name of the method parameter holding the object id. Resolved against
     * {@code @PathVariable}'s name first (falls back to its declared value), then against
     * the parameter's own name — so this works on Controller methods (with or without an
     * explicit {@code @PathVariable("...")}) and on plain Service methods alike.
     */
    String idParam();
}

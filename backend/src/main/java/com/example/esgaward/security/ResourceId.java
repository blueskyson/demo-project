package com.example.esgaward.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the {@code Long} parameter holding the id of the resource a {@link RequirePermission}
 * check applies to (an award event or proposal id, per {@link Permission#resourceType()}).
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceId {
}

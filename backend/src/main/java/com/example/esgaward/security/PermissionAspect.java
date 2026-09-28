package com.example.esgaward.security;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/** Enforces {@link RequirePermission} by asking {@link AuthorizationService} before the method runs. */
@Aspect
@Component
public class PermissionAspect {

    private final AuthorizationService authorizationService;

    public PermissionAspect(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission)
            throws Throwable {
        Permission permission = requirePermission.value();
        Long resourceId = permission.resourceType() == ResourceType.NONE ? null : resourceId(joinPoint);
        authorizationService.check(permission, resourceId);
        return joinPoint.proceed();
    }

    private static Long resourceId(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(ResourceId.class)) {
                Long id = (Long) joinPoint.getArgs()[i];
                if (id == null) {
                    throw new IllegalArgumentException("@ResourceId argument of " + method.getName() + " is null");
                }
                return id;
            }
        }
        // ServiceArchitectureTest prevents this; fail closed if it ever happens.
        throw new IllegalStateException(method + " requires a @ResourceId parameter");
    }
}

package com.example.demobackend.authorization;

import java.lang.reflect.Parameter;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

@Aspect
@Component
public class FgaCheckAspect {

    private final AuthorizationService authorizationService;

    public FgaCheckAspect(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Before("@annotation(fgaCheck)")
    public void checkAccess(JoinPoint joinPoint, FgaCheck fgaCheck) {
        String objectId = resolveIdParam(joinPoint, fgaCheck.idParam());
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        boolean allowed = authorizationService.check(
                fgaCheck.userType(), jwt.getSubject(), fgaCheck.relation(), fgaCheck.objectType(), objectId);

        if (!allowed) {
            throw new AccessDeniedException(
                    "Missing '" + fgaCheck.relation() + "' relation on " + fgaCheck.objectType() + ":" + objectId);
        }
    }

    /** Matches {@code @PathVariable}'s name first, then falls back to the parameter's own name. */
    private String resolveIdParam(JoinPoint joinPoint, String idParam) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            PathVariable pathVariable = parameters[i].getAnnotation(PathVariable.class);
            String name = (pathVariable != null && !pathVariable.value().isEmpty())
                    ? pathVariable.value()
                    : parameters[i].getName();
            if (name.equals(idParam)) {
                return String.valueOf(args[i]);
            }
        }
        throw new IllegalStateException("@FgaCheck idParam '" + idParam + "' does not match any parameter of "
                + signature.getMethod());
    }
}

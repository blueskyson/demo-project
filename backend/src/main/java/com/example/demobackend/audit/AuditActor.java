package com.example.demobackend.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Who performed the action, read from the caller's security context. */
record AuditActor(String id, String name) {

    private static final AuditActor SYSTEM = new AuditActor(null, "system");

    static AuditActor current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM;
        }
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String username = jwt.getToken().getClaimAsString("preferred_username");
            return new AuditActor(jwt.getToken().getSubject(), username != null ? username : jwt.getName());
        }
        return new AuditActor(authentication.getName(), authentication.getName());
    }
}

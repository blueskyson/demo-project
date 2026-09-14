package com.example.demobackend.web;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/public/hello")
    public Map<String, String> publicHello() {
        return Map.of("message", "Hello, this endpoint is public.");
    }

    @GetMapping("/api/private/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        List<?> roles = realmAccess != null ? (List<?>) realmAccess.get("roles") : List.of();

        return Map.of(
                "username", jwt.getClaimAsString("preferred_username"),
                "email", jwt.getClaimAsString("email"),
                "roles", roles,
                "expiresAt", jwt.getExpiresAt()
        );
    }
}

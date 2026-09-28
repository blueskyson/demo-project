package com.example.esgaward.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTests {

    @Test
    void mapsRealmRolesToUpperCaseRoleAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of("admin", "normal_user")))
                .build();

        assertThat(new KeycloakRealmRoleConverter().convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "ROLE_NORMAL_USER");
    }

    @Test
    void returnsNoAuthoritiesWithoutRealmAccess() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "x").build();

        assertThat(new KeycloakRealmRoleConverter().convert(jwt)).isEmpty();
    }
}

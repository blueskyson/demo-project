package com.example.esgaward.user;

/**
 * Application roles. They mirror the Keycloak realm roles {@code admin} / {@code normal_user},
 * which {@link com.example.esgaward.config.KeycloakRealmRoleConverter} maps to
 * {@code ROLE_ADMIN} / {@code ROLE_NORMAL_USER}.
 */
public enum UserRole {
    ADMIN,
    NORMAL_USER;

    public String authority() {
        return "ROLE_" + name();
    }
}

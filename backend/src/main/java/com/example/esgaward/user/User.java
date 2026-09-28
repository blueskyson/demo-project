package com.example.esgaward.user;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Local copy of a Keycloak user. Keycloak stays the source of truth for identity and role;
 * this row exists so proposals can reference their leader/members with foreign keys.
 */
@Entity
@Table(name = "users")
public class User {

    /** Keycloak subject ("sub" claim). */
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(UUID id, String username, String email, String displayName, UserRole role, Instant now) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Refreshes profile fields from the latest token; returns true if anything changed. */
    public boolean syncProfile(String username, String email, String displayName, UserRole role, Instant now) {
        boolean changed = !username.equals(this.username)
                || !Objects.equals(email, this.email)
                || !Objects.equals(displayName, this.displayName)
                || role != this.role;
        if (changed) {
            this.username = username;
            this.email = email;
            this.displayName = displayName;
            this.role = role;
            this.updatedAt = now;
        }
        return changed;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

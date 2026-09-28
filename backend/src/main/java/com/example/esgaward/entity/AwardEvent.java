package com.example.esgaward.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "award_event")
public class AwardEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 4000)
    private String description;

    /** Proposals under this event can no longer be edited by normal users from this instant on. */
    @Column(nullable = false)
    private Instant deadline;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AwardEvent() {
    }

    public AwardEvent(String name, String description, Instant deadline, Instant now) {
        this.name = name;
        this.description = description;
        this.deadline = deadline;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String description, Instant deadline, Instant now) {
        this.name = name;
        this.description = description;
        this.deadline = deadline;
        this.updatedAt = now;
    }

    public boolean isClosedAt(Instant now) {
        return !now.isBefore(deadline);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

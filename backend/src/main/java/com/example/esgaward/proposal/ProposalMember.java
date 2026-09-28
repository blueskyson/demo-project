package com.example.esgaward.proposal;

import java.time.Instant;

import com.example.esgaward.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Join entity for the proposal &lt;-&gt; user many-to-many relationship. */
@Entity
@Table(name = "proposal_member",
        uniqueConstraints = @UniqueConstraint(name = "uk_proposal_member", columnNames = {"proposal_id", "user_id"}))
public class ProposalMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProposalMember() {
    }

    ProposalMember(Proposal proposal, User user, Instant now) {
        this.proposal = proposal;
        this.user = user;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public User getUser() {
        return user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

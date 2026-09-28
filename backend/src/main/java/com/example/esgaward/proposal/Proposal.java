package com.example.esgaward.proposal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.esgaward.awardevent.AwardEvent;
import com.example.esgaward.user.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "proposal")
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "award_event_id", nullable = false)
    private AwardEvent awardEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leader_id", nullable = false)
    private User leader;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ProposalMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("uploadedAt ASC")
    private List<ProposalFile> files = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Proposal() {
    }

    public Proposal(AwardEvent awardEvent, User leader, String title, String description, Instant now) {
        this.awardEvent = awardEvent;
        this.leader = leader;
        this.title = title;
        this.description = description;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String title, String description, User leader, Instant now) {
        this.title = title;
        this.description = description;
        this.leader = leader;
        this.updatedAt = now;
    }

    public boolean isLedBy(User user) {
        return leader.getId().equals(user.getId());
    }

    public boolean hasMember(UUID userId) {
        return members.stream().anyMatch(m -> m.getUser().getId().equals(userId));
    }

    public void addMember(User user, Instant now) {
        members.add(new ProposalMember(this, user, now));
        this.updatedAt = now;
    }

    public boolean removeMember(UUID userId, Instant now) {
        boolean removed = members.removeIf(m -> m.getUser().getId().equals(userId));
        if (removed) {
            this.updatedAt = now;
        }
        return removed;
    }

    public void addFile(ProposalFile file, Instant now) {
        files.add(file);
        this.updatedAt = now;
    }

    public void removeFile(ProposalFile file, Instant now) {
        files.remove(file);
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public AwardEvent getAwardEvent() {
        return awardEvent;
    }

    public User getLeader() {
        return leader;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<ProposalMember> getMembers() {
        return members;
    }

    public List<ProposalFile> getFiles() {
        return files;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

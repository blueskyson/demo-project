package com.example.esgaward.security;

/**
 * Every action a service method can perform. Each permission names the {@link ResourceType} it is
 * checked against; when that is not {@link ResourceType#NONE}, the annotated method must mark the
 * resource's id parameter with {@link ResourceId} (enforced by {@code ServiceArchitectureTest}).
 * The rule for each permission lives in {@link AuthorizationService#check}.
 */
public enum Permission {
    USER_READ(ResourceType.NONE),

    AWARD_EVENT_LIST(ResourceType.NONE),
    AWARD_EVENT_READ(ResourceType.AWARD_EVENT),
    AWARD_EVENT_CREATE(ResourceType.NONE),
    AWARD_EVENT_UPDATE(ResourceType.AWARD_EVENT),
    AWARD_EVENT_DELETE(ResourceType.AWARD_EVENT),

    PROPOSAL_LIST(ResourceType.NONE),
    /** Checked against the award event the proposal is created in. */
    PROPOSAL_CREATE(ResourceType.AWARD_EVENT),
    PROPOSAL_READ(ResourceType.PROPOSAL),
    PROPOSAL_UPDATE(ResourceType.PROPOSAL),
    PROPOSAL_DELETE(ResourceType.PROPOSAL),
    PROPOSAL_MEMBER_MANAGE(ResourceType.PROPOSAL),

    PROPOSAL_FILE_READ(ResourceType.PROPOSAL),
    PROPOSAL_FILE_UPLOAD(ResourceType.PROPOSAL),
    PROPOSAL_FILE_DELETE(ResourceType.PROPOSAL);

    private final ResourceType resourceType;

    Permission(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public ResourceType resourceType() {
        return resourceType;
    }
}

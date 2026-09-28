package com.example.esgaward.security;

/** The kind of resource a {@link Permission} is checked against. */
public enum ResourceType {
    /** Not tied to a specific resource; only the user's role matters. */
    NONE,
    AWARD_EVENT,
    PROPOSAL
}

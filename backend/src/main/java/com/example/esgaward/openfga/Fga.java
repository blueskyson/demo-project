package com.example.esgaward.openfga;

import java.util.UUID;

/**
 * Object ids and relation names from {@code openfga/model.fga}, in one place so typos can't
 * silently turn into "denied".
 */
public final class Fga {

    /** The single {@code system} object that admins are related to. */
    public static final String SYSTEM = "system:esg-award";
    public static final String ALL_USERS = "user:*";

    // system
    public static final String ADMIN = "admin";
    public static final String CAN_CREATE_AWARD_EVENT = "can_create_award_event";

    // award_event
    public static final String SYSTEM_RELATION = "system";
    public static final String VIEWER = "viewer";
    public static final String OPEN = "open";
    public static final String CAN_VIEW = "can_view";
    public static final String CAN_MANAGE = "can_manage";
    public static final String CAN_CREATE_PROPOSAL = "can_create_proposal";

    // proposal
    public static final String AWARD_EVENT_RELATION = "award_event";
    public static final String LEADER = "leader";
    public static final String MEMBER = "member";
    public static final String CAN_EDIT = "can_edit";

    // conditions
    public static final String BEFORE_DEADLINE = "before_deadline";
    public static final String DEADLINE_PARAM = "deadline";
    public static final String CURRENT_TIME_PARAM = "current_time";

    public static final String PROPOSAL_TYPE = "proposal";

    private Fga() {
    }

    public static String user(UUID id) {
        return "user:" + id;
    }

    public static String awardEvent(Long id) {
        return "award_event:" + id;
    }

    public static String proposal(Long id) {
        return PROPOSAL_TYPE + ":" + id;
    }

    /** {@code "proposal:12"} -> {@code 12}. */
    public static Long idOf(String object) {
        return Long.valueOf(object.substring(object.indexOf(':') + 1));
    }
}

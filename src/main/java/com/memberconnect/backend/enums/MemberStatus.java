package com.memberconnect.backend.enums;

public enum MemberStatus {
    ACTIVE,
    RETIREMENT_REQUESTED,
    RETIREMENT_APPROVED,
    TERMINATION_REQUESTED,
    // Set when the Board approves a termination (MMT09). The member is NOT yet
    // terminated: monthly remittance stops here, but only the Finance Module may
    // move them on to TERMINATED, once every savings account has been closed
    // (MMT11). Mirrors RETIREMENT_APPROVED above.
    TERMINATION_APPROVED,
    RETIRED,
    INACTIVE,
    RESIGNED,
    TERMINATED,
    DECEASED,
    MEMBER_DEATH_RECORDED,
    SELECTED_FOR_DORMANT,
    SENT_FOR_DORMANT_APPROVAL,
    INACTIVE_DORMANT
}
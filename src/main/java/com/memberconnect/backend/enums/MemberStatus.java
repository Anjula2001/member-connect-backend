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
    // Set when a Member Death Record is approved at any of the three levels
    // (MMT22/23/24). The member is NOT yet deceased in the system: monthly
    // remittance stops here, but only the Finance Module may move them on to
    // DECEASED, once every savings account has been closed (MMT25). Mirrors
    // TERMINATION_APPROVED / RETIREMENT_APPROVED above.
    MEMBER_DEATH_APPROVED,
    SELECTED_FOR_DORMANT,
    SENT_FOR_DORMANT_APPROVAL,
    INACTIVE_DORMANT
}
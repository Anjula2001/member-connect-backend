package com.memberconnect.backend.enums;

public enum Role {
    SUPER_ADMIN,
    DISTRICT_OFFICE,
    // The three Member Death approval levels (SRS 4.1.1). The District Office
    // decides first (MMT22), escalating to the District Committee (MMT23) and
    // then the Planning & Development Committee at Head Office (MMT24). Separate
    // roles are what stop one clerk walking a record through all three levels.
    DISTRICT_COMMITTEE,
    PD_COMMITTEE,
    BOARD_SECRETARY,
    HEAD_OFFICE,
    ACCOUNTS,
    SCHOLARSHIP_OFFICER,
    DEATH_DONATION_OFFICER
}

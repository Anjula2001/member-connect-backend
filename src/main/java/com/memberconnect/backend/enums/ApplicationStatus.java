package com.memberconnect.backend.enums;

public enum ApplicationStatus {
    NEW,
    SUBMITTED_FOR_APPROVAL,
    ADDED_TO_BOARD_APPROVAL_LIST,
    REJECTED,

    /**
     * The board approved this application and a Member record was created from it.
     *
     * Deliberately distinct from INACTIVE: INACTIVE means "an authorised user
     * deactivated this application" (a manual action needing Inactive rights), whereas
     * APPROVED means "this application succeeded and became a member". Reusing INACTIVE
     * for both made the two indistinguishable in the registration list.
     *
     * Per the spec these converted records are excluded from the New Member
     * Registration List, which only shows registrations not yet approved as Members.
     */
    APPROVED,

    INACTIVE,
    PENDING,
}
package com.memberconnect.backend.enums;

/**
 * Represents the lifecycle status of a Death Donation Request.
 *
 * Flow: NEW → SUBMITTED_FOR_APPROVAL → DISTRICT_COMMITTEE → PD_COMMITTEE → APPROVED / REJECTED / INCOMPLETE
 */
public enum DeathDonationStatus {
    NEW,
    SUBMITTED_FOR_APPROVAL,
    DISTRICT_COMMITTEE,
    PD_COMMITTEE,
    APPROVED,
    REJECTED,
    INCOMPLETE,
    INACTIVE
}

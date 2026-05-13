package com.memberconnect.backend.enums;

/**
 * Represents the lifecycle status of a Member Death Record.
 */
public enum DeathRecordStatus {
    NEW,
    SUBMITTED_FOR_APPROVAL,
    DISTRICT_COMMITTEE,
    PD_COMMITTEE,
    APPROVED,
    REJECTED,
    INCOMPLETE,
    INACTIVE
}

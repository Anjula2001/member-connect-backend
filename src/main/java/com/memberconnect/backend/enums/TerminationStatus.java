package com.memberconnect.backend.enums;

public enum TerminationStatus {
    NEW("New"),
    PENDING("Pending"),
    SUBMITTED_FOR_APPROVAL("Submitted for Approval"),
    ADDED_TO_APPROVAL_LIST("Added to Approval List"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    PROCESSED("Processed"),
    CANCELLED("Cancelled"),
    INACTIVE("Inactive");

    private final String displayName;

    TerminationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

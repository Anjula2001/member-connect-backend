package com.memberconnect.backend.enums;

public enum TerminationReason {
    RETIREMENT("Retirement"),
    RESIGNATION("Resignation"),
    DEATH("Death"),
    TERMINATION_OF_SERVICE("Termination of Service"),
    VOLUNTARY_WITHDRAWAL("Voluntary Withdrawal"),
    DISMISSAL("Dismissal"),
    MEDICAL_GROUNDS("Medical Grounds"),
    OTHER("Other");

    private final String displayName;

    TerminationReason(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

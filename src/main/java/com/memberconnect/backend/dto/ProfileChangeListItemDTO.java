package com.memberconnect.backend.dto;

import java.time.LocalDate;

import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.ProfileChangeType;

import lombok.Data;

/**
 * One row of the "All Member Profile Change Requests List" (MMC02/06/15/19).
 *
 * The list is a single screen across all request types, so the row has to be the same
 * shape whichever table it came from. Member name and NIC are resolved from the Member
 * record rather than stored on the request: the SRS's search runs over Full Name, Name
 * as in Payroll, Name with Initials, Member Number and NIC, and those live on Member.
 *
 * requestId is the row's own primary key within its table, kept separate from requestNo
 * (the user-facing PCR-2026-001) because the edit and view routes address rows by key.
 */
@Data
public class ProfileChangeListItemDTO {

    private ProfileChangeType type;

    /** SRS 'Type' dropdown wording, so the client does not re-map the enum. */
    private String typeLabel;

    private Integer requestId;

    private String requestNo;

    private ApplicationStatus status;

    private LocalDate requestedDate;

    private String submissionLocation;

    // --- Resolved from the Member record ---
    private String memberId;
    private String fullName;
    private String nameAsInPayroll;
    private String nameWithInitials;
    private String nic;
}

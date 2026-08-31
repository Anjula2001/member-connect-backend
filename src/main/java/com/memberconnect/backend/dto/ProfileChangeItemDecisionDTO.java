package com.memberconnect.backend.dto;

import lombok.Data;

/**
 * One request's decision within a Name or Nominee Change Approval List
 * (Requirement 02, MMC12 / MMC25).
 *
 * The SRS is explicit that the board decides each request individually: every row
 * defaults to Approve, and any row switched to Reject must carry its own reason.
 * Processing previously took a single list-wide decision and applied it to every
 * request in the list, so one rejection rejected everybody.
 */
@Data
public class ProfileChangeItemDecisionDTO {

    /** The request's primary key within its own table. */
    private Integer requestId;

    /** "Approve" or "Reject". Absent is treated as Approve, matching the screen's default. */
    private String decision;

    /** Mandatory when decision is Reject. */
    private String rejectReason;

    public boolean isReject() {
        return decision != null && "REJECT".equalsIgnoreCase(decision.trim());
    }
}

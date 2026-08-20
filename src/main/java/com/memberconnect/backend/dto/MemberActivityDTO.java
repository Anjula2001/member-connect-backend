package com.memberconnect.backend.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * One account-activity event reported by the Finance Module (SRS 4.2.1).
 *
 * activityDate is optional and defaults to today, so a live feed can post the
 * bare event while a catch-up run can backdate it.
 */
@Data
public class MemberActivityDTO {

    private LocalDate activityDate;

    /** Free text for the audit trail, e.g. "REMITTANCE" or "SAVINGS_DEPOSIT". */
    private String source;
}

package com.memberconnect.backend.enums;

/**
 * Lifecycle of a Termination Approval List (SRS MMT05-MMT10).
 *
 * CREATED   - assembled for a Board Meeting; can be printed, deleted, processed.
 * PROCESSED - the board's decisions have been recorded. Opens read-only from
 *             here on: no Print, no Process, no Delete (MMT10).
 *
 * Previously stored as bare strings. An enum keeps the two states in one place
 * and lets Hibernate reject a typo instead of persisting it.
 */
public enum TerminationApprovalListStatus {
    CREATED,
    PROCESSED
}

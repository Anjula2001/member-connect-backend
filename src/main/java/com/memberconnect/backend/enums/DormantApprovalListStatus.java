package com.memberconnect.backend.enums;

/**
 * Lifecycle of an Inactivation Approval List for Dormant Members (SRS MMD13-MMD18).
 *
 * CREATED   - assembled for a Board Meeting; can be printed, deleted, processed.
 * PROCESSED - the board's decisions have been recorded and applied. Opens
 *             read-only from here on: no Print, no Process, no Delete (MMD18).
 *
 * Deliberately two states, where the string version carried four. IN_PROGRESS
 * and INACTIVATED existed only because recording the board's decision and
 * inactivating the members were two separate calls, so a list could sit
 * half-decided. Those are now one transaction, which removes both states and
 * with them the window in which a member could be inactivated by a list nobody
 * had approved.
 *
 * Previously stored as a bare String compared with equalsIgnoreCase at four call
 * sites. An enum keeps the states in one place and lets Hibernate reject a typo
 * instead of persisting it.
 */
public enum DormantApprovalListStatus {
    CREATED,
    PROCESSED
}

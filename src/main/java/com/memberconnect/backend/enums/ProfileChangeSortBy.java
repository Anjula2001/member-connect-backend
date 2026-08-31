package com.memberconnect.backend.enums;

/**
 * Sort options for the unified profile changes list (Requirement 02, MMC02/06/15/19):
 * "Requested Date", "Status" and "Member ID", with the SRS defaulting to Requested
 * Date, Ascending.
 */
public enum ProfileChangeSortBy {

    REQUESTED_DATE,

    STATUS,

    MEMBER_ID
}

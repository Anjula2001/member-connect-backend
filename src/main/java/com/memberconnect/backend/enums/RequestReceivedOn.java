package com.memberconnect.backend.enums;

/**
 * The "Request Received On" filter on the unified profile changes list
 * (Requirement 02, MMC02/06/15/19).
 *
 * The SRS fixes both the options and the default: "By default, 'All Days' will be
 * selected." DATE_PERIOD is the only value that reads the accompanying from/to dates.
 */
public enum RequestReceivedOn {

    /** Every request, whatever its requested date. The default. */
    ALL_DAYS,

    THIS_MONTH,

    THIS_AND_LAST_MONTH,

    /** Uses the caller's explicit from and to dates. */
    DATE_PERIOD
}

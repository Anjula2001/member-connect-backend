package com.memberconnect.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * The figures the Death Donation entitlement is calculated from that only the
 * Finance Module knows (SRS 4.2.3).
 *
 * None of these exist locally: there is no member-level Death Donation
 * remittance ledger and no Special Fixed Account for Funerals table in this
 * system. Until the Finance Module is live, FinanceDeathDonationClient returns
 * a zeroed snapshot and the user enters the real figures by hand, which the SRS
 * explicitly allows for all three editable fields.
 */
@Data
public class DeathDonationFinanceSnapshotDTO {

    /** Months the Death Donation remittance has been deducted from the member. */
    private Integer monthsRemitted;

    /** Death donations already paid to this member within the past 12 months. */
    private BigDecimal receivedPast12Months;

    /** Null when the member holds no Special Fixed Account for Funerals. */
    private String funeralAccountNo;

    /** Total credited to that account so far, excluding interest. */
    private BigDecimal funeralAccountCredited;

    /** A snapshot with nothing known, used whenever Finance is unreachable or off. */
    public static DeathDonationFinanceSnapshotDTO empty() {
        DeathDonationFinanceSnapshotDTO snapshot = new DeathDonationFinanceSnapshotDTO();
        snapshot.setMonthsRemitted(0);
        snapshot.setReceivedPast12Months(BigDecimal.ZERO);
        snapshot.setFuneralAccountNo(null);
        snapshot.setFuneralAccountCredited(BigDecimal.ZERO);
        return snapshot;
    }
}

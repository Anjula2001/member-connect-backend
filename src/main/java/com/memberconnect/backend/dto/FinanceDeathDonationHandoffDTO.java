package com.memberconnect.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * What the Finance Module is told when a Death Donation Request is approved
 * (SRS MMD08): "the system will send the details of the Death Donation approved
 * members to the Finance Module to proceed with their activities. The data in
 * this integration will be sent through APIs."
 *
 * Carries the settled entitlement breakdown rather than a single figure, because
 * Finance has two things to do with it: credit the Special Fixed Account for
 * Funerals up to its cap, and disburse whatever is left.
 */
@Data
public class FinanceDeathDonationHandoffDTO {

    private String requestNo;
    private String memberId;
    private String memberNic;

    /** Who the donation is being claimed for. */
    private String deceasedName;
    private String deceasedDate;
    private String deathCertificateNumber;
    private String relationshipToDeceased;

    private String requestedDate;
    private String approvedAt;
    private String approvedBy;

    /** The level that gave the final approval, for Finance's own audit trail. */
    private String approvalLevel;

    // ---- Settled entitlement (SRS 2.2.3) ----

    private Integer monthsRemitted;
    private BigDecimal maximumDonationAmount;
    private BigDecimal eligibleDonationAmount;
    private BigDecimal receivedPast12Months;

    /** Null when the member holds no Special Fixed Account for Funerals. */
    private String funeralAccountNo;
    private BigDecimal creditedToSpecialFixedAccount;

    /** The balance actually payable to the member. */
    private BigDecimal disburseDonationAmount;
}

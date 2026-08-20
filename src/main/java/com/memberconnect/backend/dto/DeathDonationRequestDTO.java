package com.memberconnect.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class DeathDonationRequestDTO {

    private Long id;
    private String requestNo;
    private String memberId;
    private String memberFullName;
    private String memberNameWithInitials;
    private String memberNameAsInPayroll;
    private String memberNic;
    private String memberWorkingLocation;
    private String memberEducationalDistrict;
    private String status;
    private String relationshipToDeceased;
    private String requestedDate;
    private boolean deceasedMember;
    private String deceasedMemberId;
    private String deceasedName;
    private String maidenNameIfMarried;
    private String deceasedDate;
    private String deathCertificateNumber;
    private String deceasedPlaceOfWork;
    private String concernsIdentified;
    private String incompleteReason;
    private String rejectReason;

    /** The District Office the request was raised at (MMD02 location filter). */
    private String submissionLocation;

    /** Username of the clerk who raised it; backs the self-approval guard. */
    private String createdBy;

    /** True when the requested date falls outside the configured eligible period. */
    private boolean dateRangeWarning;

    /**
     * The warning text for the "Concerns Identified" section when
     * {@link #dateRangeWarning} is set, or null. Built server-side so the limit
     * comes from configuration rather than being re-hardcoded in the browser.
     */
    private String eligiblePeriodWarning;

    // ---- Death Donation Details (SRS 2.2.3) ----

    private Integer monthsRemitted;
    private Boolean monthsRemittedEdited;
    private BigDecimal maximumDonationAmount;
    private BigDecimal eligibleDonationAmount;
    private BigDecimal receivedPast12Months;
    private Boolean receivedPast12MonthsEdited;
    private String funeralAccountNo;
    private BigDecimal funeralAccountCredited;
    private BigDecimal funeralAccountMaximum;
    private BigDecimal creditedToSpecialFixedAccount;
    private Boolean creditedToSpecialFixedEdited;
    private BigDecimal disburseDonationAmount;
    private BigDecimal donationMultiplierApplied;

    // ---- Per-level decision trail (MMD05 / MMD06 / MMD07) ----

    private String level1DecidedBy;
    private String level1DecidedAt;
    private String level2DecidedBy;
    private String level2DecidedAt;
    private String level3DecidedBy;
    private String level3DecidedAt;

    /**
     * The statuses the MMD04 matrix allows from the current one, already filtered
     * by what this caller may actually do. Empty when they may change nothing.
     * Sent so the screen offers exactly the transitions the server will accept.
     */
    private List<String> allowedStatusChanges = new ArrayList<>();

    private List<DeathDonationRelativeDTO> relatives = new ArrayList<>();
}

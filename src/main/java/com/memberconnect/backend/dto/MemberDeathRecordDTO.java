package com.memberconnect.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class MemberDeathRecordDTO {
    private Long id;
    private String recordNo;
    private String memberId;
    private String memberFullName;
    private String memberNameWithInitials;
    private String memberNic;
    private String status;
    private String informedDate;
    private String deceasedDate;
    private Long causeOfDeathId;
    private String causeOfDeathName;
    private String comment;
    private String concernsIdentified;
    private String nomineeFullName;
    private String nomineeRelationship;
    private String nomineeAddress;
    private String nomineeIdentificationType;
    private String nomineeIdentificationNumber;
    private String nomineeMobile;
    private String nomineeEmail;
    private Long nomineeBankId;
    private String nomineeBankName;
    private Long nomineeBranchId;
    private String nomineeBranchName;
    private String nomineeAccountNo;
    private BigDecimal deathDonationAmount;

    // ---- Death Donation entitlement (SRS 4.2.3) ----
    // The three *Edited flags drive the "field was edited" marker on the screen.
    private Integer monthsRemitted;
    private boolean monthsRemittedEdited;
    private BigDecimal maximumDonationAmount;
    private BigDecimal eligibleDonationAmount;
    private BigDecimal receivedPast12Months;
    private boolean receivedPast12MonthsEdited;
    private String funeralAccountNo;
    private BigDecimal funeralAccountCredited;
    private BigDecimal funeralAccountMaximum;
    private BigDecimal creditedToSpecialFixedAccount;
    private boolean creditedToSpecialFixedEdited;
    private BigDecimal disburseDonationAmount;
    private BigDecimal donationMultiplierApplied;

    /** Transient MMT20 warning; derived on read, never stored. */
    private String eligiblePeriodWarning;

    // ---- Decision trail (MMT22 / MMT23 / MMT24) ----
    private String submissionLocation;
    private String createdBy;
    private String level1DecidedBy;
    private String level1DecidedAt;
    private String level2DecidedBy;
    private String level2DecidedAt;
    private String level3DecidedBy;
    private String level3DecidedAt;
    private String incompleteReason;
    private String rejectReason;
    private boolean editable;
    private boolean submittable;
    private boolean hasLoanBalance;
    private boolean hasIndirectObligations;
    private List<MemberDeathMinorDisbursementDTO> minorDisbursements = new ArrayList<>();
    private List<MemberDeathDocumentDTO> documents = new ArrayList<>();
}

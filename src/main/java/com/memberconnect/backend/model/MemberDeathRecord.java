package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.MemberDeathRecordStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
    name = "member_death_record",
    indexes = {
        @Index(name = "idx_member_death_record_status_date", columnList = "status, informed_date"),
        @Index(name = "idx_member_death_record_location", columnList = "submission_location")
    }
)
public class MemberDeathRecord implements DonationEntitlementTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", unique = true, nullable = false)
    private String recordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MemberDeathRecordStatus status;

    @Column(name = "informed_date", nullable = false)
    private LocalDate informedDate;

    @Column(name = "deceased_date", nullable = false)
    private LocalDate deceasedDate;

    @Column(name = "cause_of_death", nullable = false, length = 200)
    private String causeOfDeath;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "concerns_identified", length = 2000)
    private String concernsIdentified;

    @Column(name = "nominee_full_name", length = 200)
    private String nomineeFullName;

    @Column(name = "nominee_relationship", length = 100)
    private String nomineeRelationship;

    @Column(name = "nominee_address", length = 500)
    private String nomineeAddress;

    @Column(name = "nominee_identification_type_and_number", length = 100)
    private String nomineeIdentificationTypeAndNumber;

    @Column(name = "nominee_mobile_no", nullable = false, length = 20)
    private String nomineeMobileNo;

    @Column(name = "nominee_email_address", length = 100)
    private String nomineeEmailAddress;

    @Column(name = "bank", nullable = false, length = 100)
    private String bank;

    @Column(name = "bank_branch", nullable = false, length = 100)
    private String bankBranch;

    @Column(name = "account_number", nullable = false, length = 100)
    private String accountNumber;

    @Column(name = "nominee_mobile")
    private String nomineeMobile;

    @Column(name = "nominee_email")
    private String nomineeEmail;

    @Column(name = "nominee_bank_id")
    private Long nomineeBankId;

    @Column(name = "nominee_branch_id")
    private Long nomineeBranchId;

    @Column(name = "nominee_account_no")
    private String nomineeAccountNo;

    @Column(name = "death_donation_amount", precision = 15, scale = 2)
    private BigDecimal deathDonationAmount;

    @Column(name = "incomplete_reason", length = 1000)
    private String incompleteReason;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    /**
     * District the record was raised in, stamped once at creation from the
     * member's own submission location (falling back to the creating user's
     * assigned district). Matched by plain equality against
     * User.assignedDistrict to scope District Office users - the same mechanism
     * as TerminationRequest.submissionLocation.
     */
    @Column(name = "submission_location")
    private String submissionLocation;

    /**
     * Username of the District Office user who created the record. Backs the
     * self-approval guard: the SRS separates the clerk who raises a record
     * (MMT18) from the "Authorized User" who decides it (MMT22), and with both
     * mapped to DISTRICT_OFFICE this is what keeps the two apart.
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    // ---------- Death Donation entitlement (SRS 4.2.3) ----------
    // Every figure below is nullable: it is only populated once the record has
    // been saved and the entitlement has been calculated.

    /** Months the Death Donation remittance was deducted. Editable by the user. */
    @Column(name = "months_remitted")
    private Integer monthsRemitted;

    @Column(name = "months_remitted_edited")
    private Boolean monthsRemittedEdited;

    @Column(name = "maximum_donation_amount", precision = 15, scale = 2)
    private BigDecimal maximumDonationAmount;

    @Column(name = "eligible_donation_amount", precision = 15, scale = 2)
    private BigDecimal eligibleDonationAmount;

    /** Donations already paid to this member within the past 12 months. Editable. */
    @Column(name = "received_past_12_months", precision = 15, scale = 2)
    private BigDecimal receivedPast12Months;

    @Column(name = "received_past_12_months_edited")
    private Boolean receivedPast12MonthsEdited;

    /** Null when the member has no Special Fixed Account for Funerals. */
    @Column(name = "funeral_account_no", length = 100)
    private String funeralAccountNo;

    /** Total credited to the funeral account so far, excluding interest. */
    @Column(name = "funeral_account_credited", precision = 15, scale = 2)
    private BigDecimal funeralAccountCredited;

    @Column(name = "funeral_account_maximum", precision = 15, scale = 2)
    private BigDecimal funeralAccountMaximum;

    /** Slice of the entitlement routed to the funeral account. Editable. */
    @Column(name = "credited_to_special_fixed_account", precision = 15, scale = 2)
    private BigDecimal creditedToSpecialFixedAccount;

    @Column(name = "credited_to_special_fixed_edited")
    private Boolean creditedToSpecialFixedEdited;

    /** Entitlement remaining after the funeral-account allocation. */
    @Column(name = "disburse_donation_amount", precision = 15, scale = 2)
    private BigDecimal disburseDonationAmount;

    /** The multiplier actually applied (2 when a funeral account exists, else 1). */
    @Column(name = "donation_multiplier_applied", precision = 6, scale = 2)
    private BigDecimal donationMultiplierApplied;

    // ---------- Per-level decision trail (MMT22 / MMT23 / MMT24) ----------

    @Column(name = "level1_decided_by", length = 100)
    private String level1DecidedBy;

    @Column(name = "level1_decided_at")
    private LocalDateTime level1DecidedAt;

    @Column(name = "level2_decided_by", length = 100)
    private String level2DecidedBy;

    @Column(name = "level2_decided_at")
    private LocalDateTime level2DecidedAt;

    @Column(name = "level3_decided_by", length = 100)
    private String level3DecidedBy;

    @Column(name = "level3_decided_at")
    private LocalDateTime level3DecidedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberDeathMinorAccount> minorAccounts = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

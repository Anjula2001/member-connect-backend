package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.DeathDonationRequestStatus;
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

/**
 * A Death Donation Request raised by a Member for a deceased relative
 * (SRS Requirement 05, section 2, MMD01-MMD08).
 *
 * Not to be confused with {@link MemberDeathRecord}, which records the death of
 * the Member themselves (Requirement 04). The two share a status vocabulary and
 * a three-level approval ladder but are otherwise unrelated records.
 */
@Getter
@Setter
@Entity
@Table(
        name = "death_donation_request",
        indexes = {
                // The close-relatives grid (MMD01) looks every other request up by
                // this column, so it earns an index rather than a full scan on each
                // Refresh click.
                @Index(name = "idx_dd_request_death_certificate", columnList = "death_certificate_number"),
                @Index(name = "idx_dd_request_submission_location", columnList = "submission_location")
        }
)
public class DeathDonationRequest implements DonationEntitlementTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", unique = true, nullable = false)
    private String requestNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeathDonationRequestStatus status;

    @Column(name = "relationship_to_deceased", nullable = false)
    private String relationshipToDeceased;

    @Column(name = "requested_date", nullable = false)
    private LocalDate requestedDate;

    @Column(name = "is_deceased_member", nullable = false)
    private boolean deceasedMember;

    @Column(name = "deceased_member_id")
    private String deceasedMemberId;

    @Column(name = "deceased_name", nullable = false)
    private String deceasedName;

    @Column(name = "maiden_name_if_married")
    private String maidenNameIfMarried;

    @Column(name = "deceased_date", nullable = false)
    private LocalDate deceasedDate;

    @Column(name = "death_certificate_number", nullable = false)
    private String deathCertificateNumber;

    @Column(name = "deceased_place_of_work")
    private String deceasedPlaceOfWork;

    @Column(name = "concerns_identified", columnDefinition = "TEXT")
    private String concernsIdentified;

    @Column(name = "incomplete_reason", columnDefinition = "TEXT")
    private String incompleteReason;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    /**
     * The District Office the request was raised at, stamped once at creation
     * from the creating user's assigned district.
     *
     * Deliberately NOT the member's own district: SRS p.12 says "The Member can
     * go to any District Office and request for a Death Donation irrespective of
     * the district of their working address", so the office that took the request
     * owns it. Matched by plain equality against User.assignedDistrict, the same
     * mechanism as TerminationRequest.submissionLocation.
     */
    @Column(name = "submission_location")
    private String submissionLocation;

    /**
     * Username of the District Office user who raised the request. Backs the
     * self-approval guard: SRS MMD01 separates the clerk who creates a request
     * from the "Authorized User" who decides it in MMD05, and with both mapped to
     * DISTRICT_OFFICE this column is what keeps the two apart.
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    // ---------- Death Donation entitlement (SRS 2.2.3, pp.22-24) ----------
    // Every figure below is nullable: they are only populated once the request
    // has been saved and the entitlement calculated.

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

    // ---------- Per-level decision trail (MMD05 / MMD06 / MMD07) ----------

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

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeathDonationRelative> relatives = new ArrayList<>();

    /**
     * SRS 2.2.3 draws the Maximum Death Donation Amount straight from the
     * configured system value, with no per-cause override (unlike Requirement 04,
     * where the Cause of Death may carry one). A donation request records no
     * cause of death at all, so this is always null.
     */
    @Override
    public String getCauseOfDeath() {
        return null;
    }

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

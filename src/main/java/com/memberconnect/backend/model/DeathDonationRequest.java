package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.DeathDonationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entity for a Death Donation Request.
 * One request → many relatives, many documents.
 */
@Getter
@Setter
@Entity
@Table(name = "DeathDonationRequest")
public class DeathDonationRequest {

    // ── Primary key ────────────────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable unique ID, e.g. "DDR-1714901234567" */
    @Column(unique = true, nullable = false)
    private String requestId;

    // ── Requesting member (must be ACTIVE) ────────────────────────────────────
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // ── Request details ───────────────────────────────────────────────────────
    /** Relationship of the requesting member to the deceased (Father, Mother, Spouse …) */
    @Column(nullable = false, length = 100)
    private String relationshipToDeceased;

    /** Date the request was created – defaults to today and cannot be a future date */
    @Column(nullable = false)
    private LocalDate requestedDate;

    /** Is the deceased person also a system member? */
    @Column(nullable = false)
    private Boolean isDeceasedMember;

    /** Member ID of the deceased (only when isDeceasedMember = true) */
    @Column(length = 50)
    private String deceasedMemberId;

    /** Full name of the deceased */
    @Column(nullable = false, length = 200)
    private String deceasedName;

    /** Maiden / alternate name (optional) */
    @Column(length = 200)
    private String maidenName;

    /** Date of death (required) */
    @Column(nullable = false)
    private LocalDate deceasedDate;

    /** Death certificate reference number (required) */
    @Column(nullable = false, length = 100)
    private String deathCertificateNumber;

    /** Place of work – optional, may be auto-populated from member profile */
    @Column(length = 200)
    private String placeOfWork;

    /** Any concerns identified by the officer (editable even after submit) */
    @Column(length = 2000)
    private String concernsIdentified;

    // ── Status & audit ────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeathDonationStatus status;

    /** Reason given when status is set to INCOMPLETE */
    @Column(length = 1000)
    private String incompleteReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    // ── Relationships ─────────────────────────────────────────────────────────

    /**
     * Close relatives (members) linked to this request.
     * CascadeType.ALL + orphanRemoval means relatives are saved / deleted with the request.
     */
    @OneToMany(mappedBy = "deathDonationRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeathDonationRelative> relatives = new ArrayList<>();

    /**
     * Uploaded documents (death certificate, NIC copy, etc.)
     */
    @OneToMany(mappedBy = "deathDonationRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeathDonationDocument> documents = new ArrayList<>();

    // ── Lifecycle hooks ───────────────────────────────────────────────────────
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (requestId == null) {
            requestId = "DDR-" + System.currentTimeMillis();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

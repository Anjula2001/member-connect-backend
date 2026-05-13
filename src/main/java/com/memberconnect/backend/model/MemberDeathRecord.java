package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.DeathRecordStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "MemberDeathRecord")
public class MemberDeathRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Auto-generated record ID (e.g., MDR-123456789) */
    @Column(unique = true, nullable = false)
    private String recordId;

    /** The deceased member */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // ── Fields to Capture ───────────────────────────────────────────────────
    @Column(nullable = false)
    private LocalDate informedDate;

    @Column(nullable = false)
    private LocalDate deceasedDate;

    @Column(nullable = false, length = 200)
    private String causeOfDeath;

    @Column(length = 1000)
    private String comment;

    @Column(length = 2000)
    private String concernsIdentified;

    // ── Nominee Details ─────────────────────────────────────────────────────
    @Column(length = 200)
    private String nomineeFullName;

    @Column(length = 500)
    private String nomineeAddress;

    @Column(length = 100)
    private String nomineeRelationship;

    @Column(length = 100)
    private String nomineeIdentificationTypeAndNumber;

    @Column(nullable = false, length = 20)
    private String nomineeMobileNo;

    @Column(length = 100)
    private String nomineeEmailAddress;

    // ── Bank Details ────────────────────────────────────────────────────────
    @Column(nullable = false, length = 100)
    private String bank;

    @Column(nullable = false, length = 100)
    private String bankBranch;

    @Column(nullable = false, length = 100)
    private String accountNumber;

    // ── Status & Audit ──────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeathRecordStatus status;

    @Column(length = 1000)
    private String incompleteReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    // ── Relationships ───────────────────────────────────────────────────────
    
    @OneToMany(mappedBy = "memberDeathRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberDeathMinorAccount> minorAccounts = new ArrayList<>();

    @OneToMany(mappedBy = "memberDeathRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberDeathDocument> documents = new ArrayList<>();

    // ── Lifecycle Hooks ─────────────────────────────────────────────────────
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (recordId == null) {
            recordId = "MDR-" + System.currentTimeMillis();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

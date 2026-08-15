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
@Table(name = "member_death_record")
public class MemberDeathRecord {

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

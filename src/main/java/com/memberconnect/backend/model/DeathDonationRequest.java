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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "death_donation_request")
public class DeathDonationRequest {

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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeathDonationRelative> relatives = new ArrayList<>();

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

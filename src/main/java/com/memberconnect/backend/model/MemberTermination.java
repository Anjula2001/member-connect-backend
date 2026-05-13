package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.TerminationReason;
import com.memberconnect.backend.enums.TerminationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "MemberTermination")
public class MemberTermination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String terminationId;

    // Many-to-One relationship: Many terminations can reference one member
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_id", referencedColumnName = "id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TerminationReason terminationReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TerminationStatus terminationStatus;

    @Column(name = "TerminationDate", nullable = false)
    private LocalDate terminationDate;

    @Column(name = "RequestedDate", nullable = false)
    private LocalDate requestedDate;

    @Column(name = "ApprovedDate")
    private LocalDate approvedDate;

    @Column(name = "ProcessedDate")
    private LocalDate processedDate;

    @Column(name = "Remarks", length = 500)
    private String remarks;

    @Column(name = "ApprovedBy")
    private String approvedBy;

    @Column(name = "ProcessedBy")
    private String processedBy;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (terminationId == null) {
            terminationId = "TERM-" + System.currentTimeMillis();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

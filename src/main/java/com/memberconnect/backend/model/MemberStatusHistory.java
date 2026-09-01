package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.MemberStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row per change to a Member's status, carrying the date the change took effect.
 *
 * Member holds only its current status, and the audit trail cannot answer "what was
 * this member's status on date X": the dormant-reactivation path writes no audit rows
 * at all, and the Termination, Retirement and Member Death rows are keyed by the
 * request's own id rather than the member's, so AuditService has to resolve a member's
 * requests before it can find them. University Scholarship eligibility is decided on the member's status on
 * the exam's last date (a past date), so that question has to be answerable.
 *
 * effectiveDate is the date the change took effect in the business sense - a
 * termination's effective date, a deceased date - and falls back to the date the
 * change was made where the flow has no such date of its own. recordedAt is when the
 * row was written and only breaks ties between two changes sharing one effective date.
 */
@Getter
@Setter
@Entity
@Table(
        name = "member_status_history",
        indexes = @Index(name = "idx_member_status_history_member_date",
                columnList = "member_id, effective_date")
)
public class MemberStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // Null for the first recorded change of a member whose earlier status is unknown
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private MemberStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private MemberStatus toStatus;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    // The flow that made the change, e.g. TERMINATION_APPROVED - for tracing only
    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @PrePersist
    void prePersist() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
        if (effectiveDate == null) {
            effectiveDate = LocalDate.now();
        }
    }
}

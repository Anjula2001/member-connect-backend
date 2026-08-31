package com.memberconnect.backend.model;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import com.memberconnect.backend.enums.MemberStatus;

/**
 * One member's place on an Inactivation Approval List, and the board's decision
 * about them (SRS MMD17).
 *
 * This exists because MMD17 makes a rejection reason mandatory <em>per member</em>,
 * and there was nowhere to put one. Termination gets away without an equivalent
 * because a TerminationRequest is itself a first-class row that can carry its own
 * status and reason; the thing on a dormant list is a Member, which is permanent,
 * reusable across many lists over many years, and shared with every other module.
 * A reason belongs to this member at this meeting, not to the person - putting it
 * on Member would let next year's list silently overwrite this year's, and lose
 * the audit trail for the earlier meeting with it.
 *
 * The table is deliberately named in the singular and is NOT the old
 * dormant_approval_list_members join table. Under ddl-auto=update Hibernate would
 * add an id column to that table without being able to populate it for existing
 * rows, and without dropping the old composite key - the first read of a legacy
 * row would then fail on a null @Id. A new table is created clean, and
 * DormantApprovalListEntryBackfillRunner copies the old rows across.
 */
@Getter
@Setter
@Entity
@Table(
        name = "dormant_approval_list_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dormant_list_member",
                columnNames = {"dormant_approval_list_id", "member_id"}
        )
)
public class DormantApprovalListMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dormant_approval_list_id")
    private DormantApprovalList approvalList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    /**
     * Member.memberId at the time of listing. The decision payload keys on the
     * business id rather than the row id, and this keeps the printed sheet
     * readable even if the member row is later archived.
     */
    @Column(name = "member_no")
    private String memberNo;

    /** "Approve" or "Reject". Null until the board sits. */
    @Column(name = "decision")
    private String decision;

    /** MMD17: mandatory for every rejected member before the list may proceed. */
    @Column(name = "reject_reason", length = 2000)
    private String rejectReason;

    /**
     * Where to put the member back if the list is deleted or they are rejected.
     * Recorded at listing time rather than assumed, which is what makes the
     * MMD15 rollback correct instead of a blanket guess at
     * SELECTED_FOR_DORMANT. Same mechanism as TerminationRequest.previousStatus.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private MemberStatus previousStatus;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;
}

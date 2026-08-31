package com.memberconnect.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.memberconnect.backend.enums.DormantApprovalListStatus;

/**
 * A list of dormant members presented to the Board for approval before bulk
 * inactivation.
 */
@Getter
@Setter
@Entity
@Table(name = "dormant_approval_list")
public class DormantApprovalList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String listId;

    @Column(name = "board_meeting_id")
    private Long boardMeetingId;

    @Column(name = "board_meeting_date")
    private LocalDate boardMeetingDate;

    /**
     * One entry per listed member, carrying that member's board decision and -
     * when rejected - the mandatory reason. orphanRemoval makes this collection
     * the single source of truth for who is on the list: a decision row for a
     * member who was removed is not a state that can be represented.
     */
    @OneToMany(mappedBy = "approvalList", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DormantApprovalListMember> entries = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DormantApprovalListStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private String processedBy;

    @Column(name = "actual_meeting_date")
    private LocalDate actualMeetingDate;

    /**
     * The list-level outcome, always DERIVED from the member decisions rather
     * than taken from the client: "Approve", "Reject", or "Mixed" when the board
     * went both ways. A list holding both outcomes must not collapse to
     * whichever verdict happened to appear first.
     */
    @Column(name = "decision")
    private String decision;

    @Column(name = "reject_reason", length = 2000)
    private String rejectReason;

    @Column(name = "board_remarks", length = 2000)
    private String boardRemarks;

    @Column(name = "inactivated_at")
    private LocalDateTime inactivatedAt;

    /** MMD17: the scanned, board-signed Inactivation Approval List. */
    @Column(name = "approved_list_document", length = 1000)
    private String approvedListDocument;

    /** Convenience for the many call sites that only want the members. */
    public List<Member> memberList() {
        return entries.stream().map(DormantApprovalListMember::getMember).toList();
    }
}

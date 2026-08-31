package com.memberconnect.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DormantApprovalListDTO {
    private Long id;
    private String listId;
    private Long boardMeetingId;
    private LocalDate boardMeetingDate;
    private LocalDate actualMeetingDate;
    private List<String> memberIds = new ArrayList<>();
    private List<DormantMemberDTO> members = new ArrayList<>();
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private String processedBy;
    private String decision;
    private String rejectReason;
    private String boardRemarks;
    private LocalDateTime inactivatedAt;

    /**
     * MMD17: one decision per listed member, sent on the process call. Every
     * member on the list must appear exactly once, and every rejection must
     * carry a reason - both checked before anything is written.
     */
    private List<DormantMemberDecisionDTO> memberDecisions = new ArrayList<>();

    /** The scanned, board-signed Inactivation Approval List. */
    private String approvedListDocument;

    // Populated on the process response so the caller can confirm what the
    // server actually applied, rather than trusting its own tally.
    private Integer approvedCount;
    private Integer rejectedCount;
}

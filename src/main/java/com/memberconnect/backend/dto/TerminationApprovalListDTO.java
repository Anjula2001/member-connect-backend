package com.memberconnect.backend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TerminationApprovalListDTO {

    private Long id;
    private String listId;
    private Long boardMeetingId;
    private LocalDate boardMeetingDate;
    private List<String> requestNos = new ArrayList<>();

    /**
     * How many termination requests the list holds.
     *
     * The list view returns this WITHOUT requestNos: the panel renders only the
     * number, and producing the request numbers meant JOIN FETCHing every request of
     * every list. Open a single list and requestNos is populated as before.
     */
    private Integer requestCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private String processedBy;
    private LocalDate actualMeetingDate;

    /**
     * The board's verdict across the whole list: "Approve", "Reject", or "Mixed"
     * when the meeting approved some requests and rejected others. Derived by the
     * server from requestDecisions - never sent by the client, because a single
     * value cannot describe a mixed list without losing information.
     */
    private String decision;

    /**
     * Retained only so an already-processed list still reads back the way it was
     * stored. Per-request reasons live on requestDecisions; this is not the place
     * to look for why an individual member was rejected.
     */
    private String rejectReason;

    private String boardRemarks;

    /** URL of the scanned, board-signed approval sheet (MMT09). */
    private String approvedListDocument;

    /**
     * Inbound on process: the board's decision for every request in the list.
     * Outbound: echoed back so the caller can confirm what was applied.
     */
    private List<TerminationRequestDecisionDTO> requestDecisions = new ArrayList<>();

    /** Server-derived summary of the processed list. */
    private int approvedCount;
    private int rejectedCount;
}

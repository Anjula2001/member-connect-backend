package com.memberconnect.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the Board Approvals list, whichever table it came from.
 *
 * The screen shows membership approval lists and termination approval lists in a
 * single table. They are separate entities with separate controllers, so the browser
 * used to fetch both in full and reconcile them into rows itself — including working
 * out what a list contains and how many items are in it. Deriving that here is what
 * lets the merged result be ordered and paged before it is sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalListRowDTO {

    /** "membership" or "termination" — which endpoint owns the list. */
    private String kind;

    /**
     * What the list holds: "applications", "name-change", "nominee-change" or
     * "termination". A list is homogeneous (MMC08/MMC21 only allow one kind in a
     * selection), so the first non-empty collection identifies it.
     */
    private String content;

    private String listId;
    private String status;
    private Long boardMeetingId;
    private LocalDate boardMeetingDate;
    private LocalDateTime createdAt;

    /** Items in the list, counted in the database rather than by loading them. */
    private int itemCount;
}

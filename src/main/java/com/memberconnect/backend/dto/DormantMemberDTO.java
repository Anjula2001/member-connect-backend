package com.memberconnect.backend.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DormantMemberDTO {
    private Long id;
    private String memberId;
    private String fullName;
    private String nameWithInitials;
    private String nic;
    private String memberType;

    /**
     * The District Office that administers this member (Member.submissionLocation).
     * This is what the Location filter matches and what a District Office caller
     * is scoped to, so the column and the filter above it agree.
     */
    private String location;

    /** The member's working district, kept for reference. Not a filter. */
    private String educationalDistrict;

    private LocalDate lastActivityDate;
    private LocalDate membershipDate;
    private LocalDate dormantSelectionDate;
    private String status;
    private boolean hasIndirectObligations;

    /** The board's decision on this member, once a list has been processed. */
    private String decision;

    /** MMD17: why the board declined to inactivate this member. */
    private String rejectReason;

    /**
     * True when the member transacted after the list was assembled - a warning
     * to the board that they are about to inactivate somebody who has since
     * become active. Computed per list, never stored.
     */
    private boolean activitySinceListing;
}

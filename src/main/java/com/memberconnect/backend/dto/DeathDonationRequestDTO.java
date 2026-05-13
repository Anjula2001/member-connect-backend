package com.memberconnect.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO sent from the frontend when creating or saving a Death Donation Request.
 *
 * Validation is done in the Service layer so we keep the DTO simple.
 */
@Getter
@Setter
public class DeathDonationRequestDTO {

    // ── Who is making the request ──────────────────────────────────────────────
    /** DB id of the requesting member (must be ACTIVE) */
    private Long memberId;

    // ── Request details ───────────────────────────────────────────────────────
    /** e.g. "Father", "Mother", "Spouse" */
    private String relationshipToDeceased;

    /** Defaults to today on the frontend; cannot be a future date */
    private LocalDate requestedDate;

    /** Is the deceased also a system member? */
    private Boolean isDeceasedMember;

    /** Only required when isDeceasedMember = true */
    private String deceasedMemberId;

    private String deceasedName;

    /** Optional maiden / alternate name */
    private String maidenName;

    private LocalDate deceasedDate;

    private String deathCertificateNumber;

    /** Optional; may be auto-populated from member profile */
    private String placeOfWork;

    /** Optional free-text; editable even after submission */
    private String concernsIdentified;

    // ── Related members ───────────────────────────────────────────────────────
    /** List of close relatives to attach */
    private List<RelativeDTO> relatives;

    // ── Inner DTO for relatives ───────────────────────────────────────────────
    @Getter
    @Setter
    public static class RelativeDTO {
        private String memberId;
        private String relationshipToDeceased;
        /** true = system-detected, false = manually added */
        private Boolean isAuto;
    }
}

package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Nominee Change Request (Requirement 02, MMC18-MMC26).
 *
 * The previous version carried two status fields — an ApplicationStatus `newStatus`
 * and a String `Status` — against a single column on the entity, so which one won
 * depended on ModelMapper's name matching and on which key the caller happened to
 * send. There is now one: `status`.
 *
 * All four "New Value" fields are mandatory per MMC18's List of Fields table.
 */
@Data
public class NommineChangeRequestDTO {

    private Integer id;

    /** Generated on submit; null on a new request, which the screen shows as "NEW". */
    private String requestNo;

    private String memberId;

    private ApplicationStatus status;

    private java.time.LocalDate requestedDate;

    private String rejectReason;

    private String submissionLocation;

    /** Who decided the request, and when. Set when the board approval list is processed. */
    private String processedBy;
    private java.time.LocalDateTime processedAt;

    // --- "Current Value" snapshot, filled in by the server at submit time from the
    // --- Member Profile. Read-only to clients: anything sent here is overwritten.
    private String oldNommineName;
    private String oldRelationship;
    private String oldNic;
    private String oldAddress;

    // --- Member Details block (MMC18): resolved from the member, not stored. ---
    private String memberFullName;
    private String memberNameWithInitials;
    private String memberNic;

    // --- Supporting document (MMC18). documentStoragePath is the S3 object key; the
    // --- screen sends it back unchanged on edit so an unrelated edit does not drop the
    // --- file. Sending it blank is what asks for the document to be removed.
    private String documentType;
    private String documentFileName;
    private String documentFileType;
    private String documentStoragePath;
    private Long documentFileSize;

    @NotBlank(message = "Nominee full name is required")
    private String newnommineName;

    @NotBlank(message = "Relationship is required")
    private String relationship;

    @NotBlank(message = "Nominee identification number is required")
    private String nic;

    @NotBlank(message = "Nominee address is required")
    private String address;
}

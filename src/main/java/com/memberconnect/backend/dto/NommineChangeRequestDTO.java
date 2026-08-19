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

    @NotBlank(message = "Nominee full name is required")
    private String newnommineName;

    @NotBlank(message = "Relationship is required")
    private String relationship;

    @NotBlank(message = "Nominee identification number is required")
    private String nic;

    @NotBlank(message = "Nominee address is required")
    private String address;
}

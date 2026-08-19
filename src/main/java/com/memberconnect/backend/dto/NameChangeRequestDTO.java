package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Name Change Request (Requirement 02, MMC05-MMC13).
 *
 * nameChangeRequestID is an Integer here to match the entity's primary key; it was a
 * String, which meant every caller had to convert and the board approval list built
 * its ids by parsing that String back to a number.
 *
 * All four "New Value" fields are mandatory per MMC05's List of Fields table. Title
 * is new: the SRS lists it first, sourced from the Title Master, and it was missing
 * from the entity, the DTO and the screen.
 */
@Data
public class NameChangeRequestDTO {

    private Integer nameChangeRequestID;

    /** Generated on submit; null on a new request, which the screen shows as "NEW". */
    private String requestNo;

    private String memberId;

    private ApplicationStatus status;

    private java.time.LocalDate requestedDate;

    private String rejectReason;

    private String submissionLocation;

    @NotBlank(message = "Title is required")
    private String newTitle;

    @NotBlank(message = "Full name is required")
    private String newFullName;

    @NotBlank(message = "Name as in payroll is required")
    private String newNameAsInPayroll;

    @NotBlank(message = "Name with initials is required")
    private String newNameWithInitials;
}

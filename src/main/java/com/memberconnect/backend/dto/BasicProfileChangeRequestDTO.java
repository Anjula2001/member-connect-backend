package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.ApplicationStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Basic Profile Information Change Request (Requirement 02, MMC01-MMC04).
 *
 * The mandatory set below is MMC01's "List of Fields" table, which had been inverted
 * in the build: the entry screen only required a field when the member's *current*
 * value was empty, and the DTO marked Mobile Number as @NotBlank when the SRS marks it
 * optional. Per the table, Date of Birth, NIC, Gender, Preferred Language, Permanent
 * Private Address, Designation and Nature of Occupation are mandatory; Private
 * Telephone Number, Mobile Number and Email Address are not.
 *
 * None of these constraints previously ran at all — no controller method took @Valid.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class BasicProfileChangeRequestDTO {

    private Integer id;

    /** Generated on submit; null on a new request, which the screen shows as "NEW". */
    private String requestNo;

    private String memberId;

    private ApplicationStatus status;

    private LocalDate requestedDate;

    private String rejectReason;

    private String submissionLocation;

    /** Who decided the request, and when (MMC04). Set by the server on approve/reject. */
    private String processedBy;
    private java.time.LocalDateTime processedAt;

    // --- "Current Value" snapshot, filled in by the server at submit time from the
    // --- Member Profile. Read-only to clients: anything sent here is overwritten.
    private LocalDate oldBirthDate;
    private String oldNIC;
    private String oldGender;
    private String oldPreferredLanguage;
    private String oldPermanentPrivateAddress;
    private String oldPrivateTelephone;
    private String oldMobileNumber;
    private String oldEmailAddress;
    private String oldDesignation;
    private String oldNatureOfOccupation;

    // --- Member Details block (MMC01): resolved from the member, not stored. ---
    private String memberFullName;
    private String memberNameWithInitials;
    private String memberNic;

    @NotNull(message = "Date of birth is required")
    private LocalDate newBirthDate;

    @NotBlank(message = "NIC is required")
    @Pattern(
            regexp = "^([0-9]{9}[xXvV]|[0-9]{12})$",
            message = "NIC must be 9 digits followed by V or X, or 12 digits"
    )
    private String newNIC;

    @NotBlank(message = "Gender is required")
    private String newGender;

    @NotBlank(message = "Preferred language is required")
    private String newPreferredLanguage;

    @NotBlank(message = "Permanent private address is required")
    private String newPermanentPrivateAddress;

    /** Optional per MMC01. */
    private String newPrivateTelephone;

    /** Optional per MMC01, but must look like a number when supplied. */
    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String newMobileNumber;

    /** Optional per MMC01. */
    @Email(message = "Enter a valid email address")
    private String newEmailAddress;

    @NotBlank(message = "Designation is required")
    private String newDesignation;

    @NotBlank(message = "Nature of occupation is required")
    private String newNatureOfOccupation;

    // --- Legacy single-document metadata; superseded by the shared documents master. ---
    @Deprecated
    private String documentType;
    @Deprecated
    private String documentFileName;
    @Deprecated
    private String documentFileType;
    @Deprecated
    private String documentStoragePath;
    @Deprecated
    private Long documentFileSize;
}

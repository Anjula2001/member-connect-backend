package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.ApplicationStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BasicProfileChangeRequestDTO {

    private Integer id;

    private String memberId;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus newStatus;

    private LocalDate newBirthDate;

    @NotBlank(message = "NIC is required")
    @Pattern(regexp = "^([0-9]{9}[x|X|v|V]|[0-9]{12})$", message = "Invalid NIC format")
    private String newNIC;

    @NotBlank(message = "Gender is required")
    private String newGender;

    private String newPreferredLanguage;

    @NotBlank(message = "Address is required")
    private String newPermanentPrivateAddress;

    @NotBlank(message = "Mobile number is required")
    private String newMobileNumber;

    @Email(message = "Invalid email format")
    private String newEmailAddress;

    private String newDesignation;

    private String newNatureOfOccupation;

    private LocalDateTime createdDate;

    // Supporting document metadata
    private String documentType;
    private String documentFileName;
    private String documentFileType;
    private String documentStoragePath;
    private Long   documentFileSize;
}

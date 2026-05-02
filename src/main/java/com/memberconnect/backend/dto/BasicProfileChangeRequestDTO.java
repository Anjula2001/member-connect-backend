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

@AllArgsConstructor
@NoArgsConstructor
@Data

public class BasicProfileChangeRequestDTO {
    private Integer Id;
    private LocalDate newDateOfBirth;
    @NotBlank
    @Pattern(regexp = "[0-9]{4}")

    @NotBlank(message = "NIC is required")
    @Pattern(regexp = "^([0-9]{9}[x|X|v|V]|[0-9]{12})$", message = "Invalid NIC format")
    private String newNIC;

    @NotBlank(message = "Gender is required")
    private String newGender;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus newStatus;

    private String newPreferredLanguage;

    @NotBlank(message = "Address is required")
    private String newPermanentPrivateAddress;

    private String newPrivateTelephone;

    @NotBlank(message = "Mobile number is required")
    private String newMobileNumber;

    @Email(message = "Invalid email format")
    private String newEmailAddress;

    private String newDesignationId;
    private String newNatureOfOccupation;

}

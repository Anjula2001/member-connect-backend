package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NameChangeRequestDTO {
    @Enumerated(EnumType.STRING)
    private ApplicationStatus newStatus;
    private String nameChangeRequestID;
    private String newTitle;
    private String newFullName;
    private String newNameAsInPayroll;
    private String newNameWithInitials;
}

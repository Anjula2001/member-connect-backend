package com.memberconnect.backend.dto;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NameChangeRequestDTO {
    private String nameChangeRequestID;
    private String newTitle;
    private String newFullName;
    private String newNameAsInPayroll;
    private String newNameWithInitials;
}

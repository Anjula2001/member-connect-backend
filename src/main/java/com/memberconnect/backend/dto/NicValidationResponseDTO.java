package com.memberconnect.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NicValidationResponseDTO {

    private boolean valid;
    private boolean duplicate;
    private String message;
}
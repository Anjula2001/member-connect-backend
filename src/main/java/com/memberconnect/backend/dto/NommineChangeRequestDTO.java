package com.memberconnect.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NommineChangeRequestDTO {
    private Integer Id;
    @NotBlank
    private String newNommineName;
    private String Status;


}

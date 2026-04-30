package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.ApplicationStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NommineChangeRequestDTO {
    @Enumerated(EnumType.STRING)
    private ApplicationStatus newStatus;
    private Integer Id;
    @NotBlank
    private String newnommineName;
    private String Status;
    private String relationship;
    private String nic;
    private String address;



}

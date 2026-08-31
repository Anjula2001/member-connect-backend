package com.memberconnect.backend.dto;

import lombok.Data;

@Data
public class UniversityMasterDto {
    private Long id;

    private String name;

    private Long universityId;
    private String universityName;
    private Long programId;
    private String programName;
    private Integer duration;
    private Double scholarshipAmount;
}

package com.memberconnect.backend.dto;

import lombok.Data;

/**
 * A row of University Scholarship master data, for the Super Admin maintenance screen.
 *
 * One shape covers all three lists rather than three near-identical DTOs: the screen
 * edits them together and they only differ by which fields are populated.
 *   - University / Program : id + name
 *   - University Programme : id + universityId + programId + duration + scholarshipAmount
 */
@Data
public class UniversityMasterDto {
    private Long id;

    /** University and Program rows only. */
    private String name;

    /** University Programme rows only. */
    private Long universityId;
    private String universityName;
    private Long programId;
    private String programName;
    private Integer duration;
    private Double scholarshipAmount;
}

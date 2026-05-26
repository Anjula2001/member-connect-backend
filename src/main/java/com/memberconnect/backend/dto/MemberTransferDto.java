package com.memberconnect.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class MemberTransferDto {

    private Long memberId;
    private LocalDate requestedDate;

    private Long newWorkingLocationTypeId;
    private Long newEducationalDistrictId;
    private Long newEducationalZoneId;
    private Long newWorkingLocationId;
    private Long newDesignationId;
    private Long newNatureOfOccupationId;

    private String newWorkingLocationAddress;
    private String newSalaryPayingOffice;
    private String newComputerNoInPayslip;
}

package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.Gender;
import com.memberconnect.backend.enums.Language;
import com.memberconnect.backend.enums.NatureOfOccupation;
import com.memberconnect.backend.enums.Identification;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MemberApplicationDTO {
    private Long id;
    private String applicationID;
    private ApplicationStatus status;
    private String title;
    private String fullName;
    private String applicationDate;
    private String nameAsInPayroll;
    private String nameWithInitials;
    private String nicNumber;
    private LocalDate dateOfBirth;
    private Gender gender;
    private Language preferredLanguage;
    private String permanentPrivateAddress;
    private String workingLocationType;
    private String designation;
    private String computerNoInPayslip;
    private NatureOfOccupation natureOfOccupation;
    private String salaryPayingOffice;
    private String educationalDistrict;
    private String officeTelephone;
    private String educationalZone ;
    private String workingLocation ;
    private String workingLocationAddress;
    private String privateTelephone;
    private String mobileNumber;
    private String emailAddress;
    private String nomineeFullName;
    private String nomineeRelationship;
    private Identification identification;
    private String identificationNumber;
    private String identificationDetails;
    private String nomineeAddress;
    private BigDecimal shareAccountAmount;
    private BigDecimal specialDepositAmount;
    private BigDecimal fixedDepositAmount;
    private BigDecimal scholarshipDeathDonationPensionAmount;
    private Boolean rejoinFlag;

}
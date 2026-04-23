package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.Gender;
import com.memberconnect.backend.enums.Language;
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
    private String nameAsInPayroll;
    private String nameWithInitials;
    private String nicNumber;
    private LocalDate dateOfBirth;
    private Gender gender;
    private Language preferredLanguage;
    private String permanentPrivateAddress;
    private String computerNoInPayslip;
    private String salaryPayingOffice;
    private String officeTelephone;
    private String privateTelephone;
    private String mobileNumber;
    private String emailAddress;

    private BigDecimal shareAccountAmount;
    private BigDecimal specialDepositAmount;
    private BigDecimal fixedDepositAmount;
    private BigDecimal scholarshipDeathDonationPensionAmount;

    private Boolean rejoinFlag;
}
package com.memberconnect.backend.model;


import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.Language;
import com.memberconnect.backend.enums.Gender;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "Member_Application")
public class Member_Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String applicationID;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    @Column(name = "Title")
    private String title;

    @Column(name = "FullName")
    private String fullName;

    @Column(name = "NameAsInPayroll")
    private String nameAsInPayroll;

    @Column(name = "NameWithInitials")
    private String nameWithInitials;

    @Column(name = "NicNumber")
    private String nicNumber;

    @Column(name = "DateOfBirth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private Language preferredLanguage;

    @Column(name = "PermanentPrivateAddress")
    private String permanentPrivateAddress;

    @Column(name = "ComputerNoInPayslip")
    private String computerNoInPayslip;

    @Column(name = "SalaryPayingOffice")
    private String salaryPayingOffice;

    @Column(name = "OfficeTelephone")
    private String officeTelephone;

    @Column(name = "PrivateTelephone")
    private String privateTelephone;

    @Column(name = "MobileNumber")
    private String mobileNumber;

    @Column(name = "EmailAddress")
    private String emailAddress;

    @Column(name = "ShareAccountAmount")
    private BigDecimal shareAccountAmount;

    @Column(name = "SpecialDepositAmount")
    private BigDecimal specialDepositAmount;

    @Column(name = "FixedDepositAmount")
    private BigDecimal fixedDepositAmount;

    @Column(name = "ScholarshipDeathDonationPensionAmount")
    private BigDecimal scholarshipDeathDonationPensionAmount;

    @Column(name = "RejoinFlag")
    private Boolean rejoinFlag;

}

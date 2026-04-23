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
    private String Title;

    @Column(name = "FullName")
    private String FullName;

    @Column(name = "NameAsInPayroll")
    private String NameAsInPayroll;

    @Column(name = "NameWithInitials")
    private String NameWithInitials;

    @Column(name = "NicNumber")
    private String NicNumber;

    @Column(name = "DateOfBirth")
    private LocalDate DateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private Language preferredLanguage;

    @Column(name = "PermanentPrivateAddress")
    private String PermanentPrivateAddress;

    @Column(name = "ComputerNoInPayslip")
    private String ComputerNoInPayslip;

    @Column(name = "SalaryPayingOffice")
    private String SalaryPayingOffice;

    @Column(name = "OfficeTelephone")
    private String OfficeTelephone;

    @Column(name = "PrivateTelephone")
    private String PrivateTelephone;

    @Column(name = "MobileNumber")
    private String MobileNumber;

    @Column(name = "EmailAddress")
    private String EmailAddress;

    @Column(name = "ShareAccountAmount")
    private BigDecimal ShareAccountAmount;

    @Column(name = "SpecialDepositAmount")
    private BigDecimal SpecialDepositAmount;

    @Column(name = "FixedDepositAmount")
    private BigDecimal FixedDepositAmount;

    @Column(name = "ScholarshipDeathDonationPensionAmount")
    private BigDecimal ScholarshipDeathDonationPensionAmount;

    @Column(name = "RejoinFlag")
    private Boolean RejoinFlag;

}


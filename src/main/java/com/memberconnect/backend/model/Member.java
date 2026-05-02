package com.memberconnect.backend.model;

import java.time.LocalDate;

import com.memberconnect.backend.enums.Gender;
import com.memberconnect.backend.enums.Identification;
import com.memberconnect.backend.enums.Language;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.NatureOfOccupation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "Member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "application_id", referencedColumnName = "id")
    private Member_Application application;

    @Column(unique = true)
    private String memberId;

    @Column(name = "MemberType")
    private String memberType;

    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    @Column(name = "MembershipStartDate")
    private LocalDate membershipStartDate;

    @Column(name = "Nic")
    private String nic;

    @Column(name = "Title")
    private String title;

    @Column(name = "FullName")
    private String fullName;

    @Column(name = "NameAsInPayroll")
    private String nameAsInPayroll;

    @Column(name = "name_with_initials")
    private String nameWithInitials;

    @Column(name = "DateOfBirth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private Language preferredLanguage;

    @Column(name = "PermanentPrivateAddress")
    private String permanentPrivateAddress;

    @Column(name = "PrivateTelephone")
    private String privateTelephone;

    @Column(name = "MobileNumber")
    private String mobileNumber;

    @Column(name = "EmailAddress")
    private String emailAddress;

    @Column(name = "ComputerNoInPayslip")
    private String computerNoInPayslip;

    @Column(name = "SalaryPayingOffice")
    private String salaryPayingOffice;


    @Column(name = "ProfilePictureUrl")
    private String profilePictureUrl;

    @Column(name = "SignatureUrl")
    private String signatureUrl;

    @Column(name = "WorkingLocationType")
    private String workingLocationType;

    @Column(name = "Designation")
    private String designation;

    @Enumerated(EnumType.STRING)
    private NatureOfOccupation natureOfOccupation;

    @Column(name = "EducationalDistrict")
    private String educationalDistrict; 

    @Column(name = "EducationalZone")
    private String educationalZone;

    @Column(name = "WorkingLocation")
    private String workingLocation;

    @Column(name = "WorkingLocationAddress")
    private String workingLocationAddress;

    @Column(name = "OfficeTelephone")
    private String officeTelephone;

    @Column(name = "NomineeFullName")
    private String nomineeFullName;

    @Column(name = "NomineeRelationship")
    private String nomineeRelationship;

    @Column(name = "NomineeAddress")
    private String nomineeAddress;

    @Enumerated(EnumType.STRING)
    private Identification identification;

    @Column(name = "IdentificationNumber")
    private String identificationNumber; //The Number and the Identification Type set in the Member Registration Application.

    @Column(name = "IdentificationDetails", length = 2000)
    private String identificationDetails;

}

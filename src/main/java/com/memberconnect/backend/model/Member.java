package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.Language;
import com.memberconnect.backend.enums.Gender;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "application_id", referencedColumnName = "id")
    private Member_Application application;

    @Column(unique = true)
    private String memberId;

    @Column(name = "member_type")
    private String memberType;

    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    @Column(name = "membership_start_date")
    private LocalDate membershipStartDate;

    @Column(name = "nic")
    private String nic;

    @Column(name = "title")
    private String title;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "name_as_in_payroll")
    private String nameAsInPayroll;

    @Column(name = "name_with_initials")
    private String nameWithInitials;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private Language preferredLanguage;

    @Column(name = "permanent_private_address")
    private String permanentPrivateAddress;

    @Column(name = "private_telephone")
    private String privateTelephone;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @Column(name = "email_address")
    private String emailAddress;
    @ManyToOne
    @JoinColumn(name = "designation_id")
    private Designation designation;

    @ManyToOne
    @JoinColumn(name = "nature_of_occupation_id")
    private NatureOfOccupation natureOfOccupation;

    @ManyToOne
    @JoinColumn(name = "working_location_type_id")
    private WorkingLocationType workingLocationType;

    @ManyToOne
    @JoinColumn(name = "working_location_id")
    private WorkingLocation workingLocation;

    @ManyToOne
    @JoinColumn(name = "educational_zone_id")
    private EducationalZone educationalZone;

    @ManyToOne
    @JoinColumn(name = "educational_district_id")
    private EducationalDistrict educationalDistrict;

    @Column(name = "working_location_address")
    private String workingLocationAddress;

    @Column(name = "computer_no_in_payslip")
    private String computerNoInPayslip;

    @Column(name = "salary_paying_office")
    private String salaryPayingOffice;

    public Member() {}
}
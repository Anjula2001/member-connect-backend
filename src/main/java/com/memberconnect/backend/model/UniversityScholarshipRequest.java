package com.memberconnect.backend.model;


import com.memberconnect.backend.enums.UniversityScholarshipRequestStatus;
import com.memberconnect.backend.enums.ApplicantType;
import com.memberconnect.backend.enums.MinorAccount;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "University_Scholarship_Request")
public class UniversityScholarshipRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String UniversityScholarshipRequestID;

    @Enumerated(EnumType.STRING)
    private UniversityScholarshipRequestStatus status;

    @ManyToOne
    @JoinColumn(name = "MemberId", referencedColumnName = "memberId")
    private Member MemberId;

    @Column(name = "RequestDate")
    private LocalDate RequestDate;

    @Column(name = "StudentName")
    private String StudentName;

    @Column(name = "BirthCertificateNumber")
    private String BirthCertificateNumber;

    @Column(name = "Address")
    private String Address;

    @Column(name = "NICNumber")
    private String NICNumber;

    @Enumerated(EnumType.STRING)
    private ApplicantType ApplicantType;

    @Column(name = "Examyear")
    private String Examyear;

    @Column(name = "ExamNumber")
    private String ExamNumber;

    @Column(name = "ZScore")
    private String ZScore;

    @Column(name = "University")
    private String University;

    @Column(name = "Program")
    private String Program;

    @Column(name = "Duration")
    private String Duration;

    @Column(name = "AcademicYearStartDate")
    private LocalDate AcademicYearStartDate;

    @Enumerated(EnumType.STRING)
    private MinorAccount HasMinorAccount;

    @Column(name = "MinorAccountMonths")
    private String MinorAccountMonths;

    @Column(name = "Bank")
    private String Bank;

    @Column(name = "Branch")
    private String Branch;

    @Column(name = "AccountNumber")
    private String AccountNumber;
}



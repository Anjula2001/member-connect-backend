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
    private LocalDate requestDate;

    @Column(name = "StudentName")
    private String studentName;

    @Column(name = "BirthCertificateNumber")
    private String birthCertificateNumber;

    @Column(name = "Address")
    private String address;

    @Column(name = "NICNumber")
    private String nicNumber;

    @Column(name = "Mobile")
    private String mobile;

    @Enumerated(EnumType.STRING)
    private ApplicantType ApplicantType;

    @Column(name = "Examyear")
    private String examYear;

    @Column(name = "ExamNumber")
    private String examNumber;

    @Column(name = "ZScore")
    private String zScore;

    @ManyToOne
    @JoinColumn(name = "university_id")
    private University university;

    @ManyToOne
    @JoinColumn(name = "program_id")
    private Program program;

    private String Duration;

    @Column(name = "AcademicYearStartDate")
    private LocalDate academicYearStartDate;

    @Enumerated(EnumType.STRING)
    private MinorAccount hasMinorAccount;

    @Column(name = "MinorAccountMonths")
    private String minorAccountMonths;

    @ManyToOne
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "AccountNumber")
    private String accountNumber;

    public UniversityScholarshipRequest() {}
    
    public Long getId() { return id; }

    public LocalDate getRequestDate() { return requestDate; }
    public void setRequestDate(LocalDate requestDate) { this.requestDate = requestDate; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getNic() { return nicNumber; }
    public void setNic(String nic) { this.nicNumber = nic; }

    public String getBcNo() { return birthCertificateNumber; }
    public void setBcNo(String bcNo) { this.birthCertificateNumber = bcNo; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public Boolean getIsSchoolApplicant() { return ApplicantType == com.memberconnect.backend.enums.ApplicantType.SCHOOL_APPICANT; }
    public void setIsSchoolApplicant(com.memberconnect.backend.enums.ApplicantType SCHOOL_APPLICANT) { ApplicantType = SCHOOL_APPLICANT; }

    public String getExamYear() { return examYear; }
    public void setExamYear(String examYear) { this.examYear = examYear; }

    public String getExamNo() { return examNumber; }
    public void setExamNo(String examNo) { this.examNumber = examNo; }

    public String getZScore() { return zScore; }
    public void setZScore(String zScore) { this.zScore = zScore; }

    public LocalDate getAcademicYearStart() { return academicYearStartDate; }
    public void setAcademicYearStart(LocalDate academicYearStart) { this.academicYearStartDate = academicYearStart; }

    public String getAccountNo() { return accountNumber; }
    public void setAccountNo(String accountNo) { this.accountNumber = accountNo; }

    public Bank getBank() { return bank; }
    public void setBank(Bank bank) { this.bank = bank; }

    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }

    public University getUniversity() { return university; }
    public void setUniversity(University university) { this.university = university; }

    public Program getProgram() { return program; }
    public void setProgram(Program program) { this.program = program; }

    public String getDuration() { return Duration; }
    public void setDuration(String duration) { this.Duration = duration; }
}




package com.memberconnect.backend.dto;

import java.time.LocalDate;

public class UniversityScholarshipListDto {

    private Long id;
    private String memberId;
    private String requestId;
    private String studentName;
    private String memberName;
    private String universityName;
    private String status;
    private String nic;
    private String birthCertificateNumber;
    private String address;
    private String mobile;
    private String applicantType;
    private String examYear;
    private String examNumber;
    private String zScore;
    private String programName;
    private String duration;
    private LocalDate requestDate;
    private LocalDate academicYearStartDate;
    private String hasMinorAccount;
    private String minorAccountMonths;
    private String bankName;
    private String branchName;
    private String accountNumber;
    private String incompleteReason;


    public UniversityScholarshipListDto(
            Long id,
            String memberId,
            String requestId,
            String studentName,
            String memberName,
            String universityName,
            String status,
            String nic,
            String birthCertificateNumber,
            String address,
            String mobile,
            String applicantType,
            String examYear,
            String examNumber,
            String zScore,
            String programName,
            String duration,
            LocalDate requestDate,
            LocalDate academicYearStartDate,
            String hasMinorAccount,
            String minorAccountMonths,
            String bankName,
            String branchName,
            String accountNumber,
            String incompleteReason
    ) {
        this.id = id;
        this.memberId = memberId;
        this.requestId = requestId;
        this.studentName = studentName;
        this.memberName = memberName;
        this.universityName = universityName;
        this.status = status;
        this.nic = nic;
        this.birthCertificateNumber = birthCertificateNumber;
        this.address = address;
        this.mobile = mobile;
        this.applicantType = applicantType;
        this.examYear = examYear;
        this.examNumber = examNumber;
        this.zScore = zScore;
        this.programName = programName;
        this.duration = duration;
        this.requestDate = requestDate;
        this.academicYearStartDate = academicYearStartDate;
        this.hasMinorAccount = hasMinorAccount;
        this.minorAccountMonths = minorAccountMonths;
        this.bankName = bankName;
        this.branchName = branchName;
        this.accountNumber = accountNumber;
        this.incompleteReason = incompleteReason;
    }

    public Long getId() { 
        return id; 
    }

    public String getMemberId() { 
        return memberId; 
    }

    public String getRequestId() { 
        return requestId; 
    }

    public String getStudentName() { 
        return studentName; 
    }

    public String getMemberName() {
         return memberName;
    }

    public String getUniversityName() { 
        return universityName; 
    }

    public String getStatus() { 
        return status; 
    }

    public String getNic() { 
        return nic; 
    }

    public String getBirthCertificateNumber() { 
        return birthCertificateNumber; 
    }

    public String getAddress() { 
        return address; 
    }

    public String getMobile() { 
        return mobile; 
    }

    public String getApplicantType() {
         return applicantType; 
    }

    public String getExamYear() { 
        return examYear; 
    }

    public String getExamNumber() { 
        return examNumber; 
    }

    public String getZScore() { 
        return zScore; 
    }

    public String getProgramName() { 
        return programName; 
    }

    public String getDuration() { 
        return duration; 
    }

    public LocalDate getRequestDate() { 
        return requestDate; 
    }

    public LocalDate getAcademicYearStartDate() { 
        return academicYearStartDate; 
    }

    public String getHasMinorAccount() { 
        return hasMinorAccount; 
    }

    public String getMinorAccountMonths() { 
        return minorAccountMonths; 
    }

    public String getBankName() { 
        return bankName; 
    }

    public String getBranchName() { 
        return branchName; 
    }

    public String getAccountNumber() { 
        return accountNumber; 
    }

    public String getIncompleteReason() { 
        return incompleteReason; 
    }
}

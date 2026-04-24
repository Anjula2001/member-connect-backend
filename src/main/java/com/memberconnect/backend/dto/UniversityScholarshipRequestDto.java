package com.memberconnect.backend.dto;

import java.time.LocalDate;

public class UniversityScholarshipRequestDto {

    private LocalDate requestDate;
    private String studentName;
    private String nic;
    private String bcNo;
    private String address;
    private String mobile;
    private Boolean isSchoolApplicant;
    private String examYear;
    private String examNo;
    private String zScore;

    // These names must match frontend field names
    private String university;
    private String program;

    private String duration;
    private LocalDate academicYearStart;
    private String accountNo;
    private String bank;
    private String branch;

    public UniversityScholarshipRequestDto() {}

    public LocalDate getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(LocalDate requestDate) {
        this.requestDate = requestDate;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getNic() {
        return nic;
    }

    public void setNic(String nic) {
        this.nic = nic;
    }

    public String getBcNo() {
        return bcNo;
    }

    public void setBcNo(String bcNo) {
        this.bcNo = bcNo;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public Boolean getIsSchoolApplicant() {
        return isSchoolApplicant;
    }

    public void setIsSchoolApplicant(Boolean isSchoolApplicant) {
        this.isSchoolApplicant = isSchoolApplicant;
    }

    public String getExamYear() {
        return examYear;
    }

    public void setExamYear(String examYear) {
        this.examYear = examYear;
    }

    public String getExamNo() {
        return examNo;
    }

    public void setExamNo(String examNo) {
        this.examNo = examNo;
    }

    public String getZScore() {
        return zScore;
    }

    public void setZScore(String zScore) {
        this.zScore = zScore;
    }

    public String getUniversity() {
        return university;
    }

    public void setUniversity(String university) {
        this.university = university;
    }

    public String getProgram() {
        return program;
    }

    public void setProgram(String program) {
        this.program = program;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public LocalDate getAcademicYearStart() {
        return academicYearStart;
    }

    public void setAcademicYearStart(LocalDate academicYearStart) {
        this.academicYearStart = academicYearStart;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }

    public String getBank() {
        return bank;
    }

    public void setBank(String bank) {
        this.bank = bank;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }
}
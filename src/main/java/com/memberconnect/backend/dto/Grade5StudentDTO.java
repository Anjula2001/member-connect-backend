package com.memberconnect.backend.dto;

public class Grade5StudentDTO{

    private String studentName;
    private String birthCertificateNumber;
    private String studentSchool;
    private String schoolDistrict;
    private Integer examYear;
    private String examinationNumber;
    private Integer districtCutOffMark;
    private Integer marksObtained;
    private String requestedDate;
    private Boolean minorAccountExists;
    private String minorAccountNumber;
    private Integer eligibleMonths;
    private String disbursementOption;
    private Integer memberAmount;
    private Integer minorAmount;
    private Boolean isDoubleAmount;
  
    public String getStudentName() { 
        return studentName; 
    }
    public void setStudentName(String studentName) { 
        this.studentName = studentName;
    }

    public String getBirthCertificateNumber() {
        return birthCertificateNumber; 
    }
    public void setBirthCertificateNumber(String birthCertificateNumber) {
        this.birthCertificateNumber = birthCertificateNumber;
    }

    public String getStudentSchool() { 
        return studentSchool; 
    }
    public void setStudentSchool(String studentSchool) {
        this.studentSchool = studentSchool;
    }

    public String getSchoolDistrict() { 
        return schoolDistrict; 
    }
    public void setSchoolDistrict(String schoolDistrict) {
        this.schoolDistrict = schoolDistrict;
    }

    public Integer getExamYear() { 
        return examYear; 
    }
    public void setExamYear(Integer examYear) {
        this.examYear = examYear; 
    }

    public String getExaminationNumber() { 
        return examinationNumber; 
    }
    public void setExaminationNumber(String examinationNumber) {
        this.examinationNumber = examinationNumber;
    }

    public Integer getDistrictCutOffMark() { 
        return districtCutOffMark; 
    }
    public void setDistrictCutOffMark(Integer districtCutOffMark) {
        this.districtCutOffMark = districtCutOffMark;
    }

    public Integer getMarksObtained() { 
        return marksObtained; 
    }
    public void setMarksObtained(Integer marksObtained) {
        this.marksObtained = marksObtained;
    }

    public String getRequestedDate() {
        return requestedDate;
    }
    public void setRequestedDate(String requestedDate) {
        this.requestedDate = requestedDate;
    }

    public Boolean getMinorAccountExists() { 
        return minorAccountExists; 
    }
    public void setMinorAccountExists(Boolean minorAccountExists) { 
        this.minorAccountExists = minorAccountExists; 
    }

    public String getMinorAccountNumber() { 
        return minorAccountNumber;
    }
    public void setMinorAccountNumber(String minorAccountNumber) { 
        this.minorAccountNumber = minorAccountNumber;
    }

    public Integer getEligibleMonths() { 
        return eligibleMonths; 
    }
    public void setEligibleMonths(Integer eligibleMonths) {
        this.eligibleMonths = eligibleMonths; 
    }

    public String getDisbursementOption() { 
        return disbursementOption; 
    }
    public void setDisbursementOption(String disbursementOption) {
        this.disbursementOption = disbursementOption; 
    }

    public Integer getMemberAmount() {
        return memberAmount; 
    }
    public void setMemberAmount(Integer memberAmount) {
        this.memberAmount = memberAmount;
    }

    public Integer getMinorAmount() { 
        return minorAmount; 
    }
    public void setMinorAmount(Integer minorAmount) { 
        this.minorAmount = minorAmount; 
    }

    public Boolean getIsDoubleAmount() { 
        return isDoubleAmount; 
    }
    public void setIsDoubleAmount(Boolean isDoubleAmount) {
         this.isDoubleAmount = isDoubleAmount; 
    }
}

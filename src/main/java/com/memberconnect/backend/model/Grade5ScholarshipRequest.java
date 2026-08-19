package com.memberconnect.backend.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "Grade5ScholarshipRequest")
public class Grade5ScholarshipRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "RequestedDate")
    private LocalDate requestedDate;

    @Column(name = "StudentName")
    private String studentName;

    @Column(name = "RequestNo")
    private String requestNo;

    @Column(name = "MemberId")
    private String memberId;

    @Column(name = "Status")
    private String status;

    @Column(name = "BirthCertificateNumber")
    private String birthCertificateNumber;

    @Column(name = "StudentSchool")
    private String studentSchool;

    @Column(name = "SchoolDistrict")
    private String schoolDistrict;

    @Column(name = "ExamYear")
    private Integer examYear;

    @Column(name = "ExaminationNumber")
    private String examinationNumber;

    @Column(name = "MarksObtained")
    private Integer marksObtained;

    @Column(name = "DistrictCutOffMark")
    private Integer districtCutOffMark;

    @Column(name = "has_deviation")
    private Boolean hasDeviation;

    @Column(name = "IncompleteReason")
    private String incompleteReason;

    @Column(name = "MinorAccountExists")
    private Boolean minorAccountExists;

    @Column(name = "MinorAccountNumber")
    private String minorAccountNumber;

    @Column(name = "EligibleMonths")
    private Integer eligibleMonths;

    @Column(name = "DisbursementOption")
    private String disbursementOption;

    @Column(name = "MemberAmount")
    private Integer memberAmount;

    @Column(name = "MinorAmount")
    private Integer minorAmount;

    @Column(name = "IsDoubleAmount")
    private Boolean isDoubleAmount;

    @Column(name = "ApprovalListId")
    private String approvalListId;

    @Column(name = "OriginalStatus")
    private String originalStatus;

    /**
     * The District Office this request belongs to, for the Location filter in 2.3.2.
     *
     * Copied from the member's SubmissionLocation at save time rather than from the
     * logged-in user, because the SRS lets a member apply at any District Office
     * "irrespective of the district of their working address" — the request stays
     * with the office that administers the member, not whichever desk keyed it in.
     * Deliberately a snapshot: moving a member later must not silently relocate
     * scholarship history that a Board already acted on.
     *
     * Null means "legacy row, created before location was captured" and is treated
     * as visible to everyone rather than invisible to everyone.
     */
    @Column(name = "SubmissionLocation")
    private String submissionLocation;

    @Column(name = "CreatedBy")
    private String createdBy;

    @Column(name = "CreatedAt")
    private java.time.LocalDateTime createdAt;

    public String getSubmissionLocation() {
        return submissionLocation;
    }

    public void setSubmissionLocation(String submissionLocation) {
        this.submissionLocation = submissionLocation;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getApprovalListId() {
        return approvalListId;
    }

    public void setApprovalListId(String approvalListId) {
        this.approvalListId = approvalListId;
    }

    public String getOriginalStatus() {
        return originalStatus;
    }

    public void setOriginalStatus(String originalStatus) {
        this.originalStatus = originalStatus;
    }


    public Integer getDistrictCutOffMark() {
        return districtCutOffMark;
    }

    public void setDistrictCutOffMark(Integer districtCutOffMark) {
        this.districtCutOffMark = districtCutOffMark;
    }

    public Boolean getHasDeviation() {
        if ("SUBMITTED_FOR_DEVIATION_APPROVAL".equals(status) || "ADDED_TO_SCHOLARSHIP_DEVIATION_APPROVAL_LIST".equals(status)) {
            return true;
        }
        if ("SUBMITTED_FOR_NORMAL_APPROVAL".equals(status) || "ADDED_TO_SCHOLARSHIP_NORMAL_APPROVAL_LIST".equals(status)) {
            return false;
        }
        if (approvalListId != null && (approvalListId.contains("-DAL-") || approvalListId.contains("DAL"))) {
            return true;
        }
        if (originalStatus != null && originalStatus.contains("DEVIATION")) {
            return true;
        }
        return Boolean.TRUE.equals(hasDeviation);
    }

    public void setHasDeviation(Boolean hasDeviation) {
        this.hasDeviation = hasDeviation;
    }
    
    public Long getId() { 
        return id; 
    }

    public String getExaminationNumber() { 
        return examinationNumber; 
    }
    public void setExaminationNumber(String examinationNumber) {
        this.examinationNumber = examinationNumber;
    }

    public String getStudentName() { 
        return studentName;
    }
    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getSchool() {
         return studentSchool; 
    }
    public void setSchool(String school) { 
        this.studentSchool = school; 
    }

    public String getDistrict() {
         return schoolDistrict;
     }
    public void setDistrict(String district) { 
        this.schoolDistrict = district;
    }

    public int getExamYear() {
         return examYear; 
    }
    public void setExamYear(Integer examYear) {
         this.examYear = examYear; 
    }

    public String getBirthCertificateNumber() {
         return birthCertificateNumber;
    }
    public void setBirthCertificateNumber(String birthCertificateNumber) {
        this.birthCertificateNumber = birthCertificateNumber;
   }

    public int getMarksObtained() { 
        return marksObtained; 
    }
    public void setMarksObtained(Integer marksObtained) {
        this.marksObtained = marksObtained;
    }

    public String getRequestNo() {
        return requestNo;
    }

    public void setRequestNo(String requestNo) {
        this.requestNo = requestNo;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(LocalDate requestedDate) {
        this.requestedDate = requestedDate;
    }

     public String getIncompleteReason() {
        return incompleteReason;
    }

    public void setIncompleteReason(String incompleteReason) {
        this.incompleteReason = incompleteReason;
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

package com.memberconnect.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    private String submissionLocation;
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
    private Boolean specialDegree;
    private String incompleteReason;
    private Long boardMeetingId;
    private String boardMeetingName;
    private String approvalListId;
    private Double totalScholarshipAmount;
    private Double totalDisbursedAmount;
    private LocalDate lastDisbursementDate;
    private Integer availablePeriod;
    private Integer totalUniversityScholarships;
    private java.util.List<UniversityScholarshipFundRequestDto> fundRequests;
    private String processedBy;
    private LocalDateTime processedAt;
    private LocalDate scheduledDate;
    private String rejectReason;
    private String scannedReportPath;
    private Boolean followDeviationProcess;

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
            Boolean specialDegree,
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
        this.specialDegree = specialDegree;
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

    public Boolean getSpecialDegree() {
        return specialDegree;
    }

    public String getIncompleteReason() { 
        return incompleteReason; 
    }

    public Long getBoardMeetingId() {
        return boardMeetingId;
    }

    public void setBoardMeetingId(Long boardMeetingId) {
        this.boardMeetingId = boardMeetingId;
    }

    public String getBoardMeetingName() {
        return boardMeetingName;
    }

    public void setBoardMeetingName(String boardMeetingName) {
        this.boardMeetingName = boardMeetingName;
    }

    public Double getTotalScholarshipAmount() {
        return totalScholarshipAmount;
    }

    public void setTotalScholarshipAmount(Double totalScholarshipAmount) {
        this.totalScholarshipAmount = totalScholarshipAmount;
    }

    public Double getTotalDisbursedAmount() {
        return totalDisbursedAmount;
    }

    public void setTotalDisbursedAmount(Double totalDisbursedAmount) {
        this.totalDisbursedAmount = totalDisbursedAmount;
    }

    public LocalDate getLastDisbursementDate() {
        return lastDisbursementDate;
    }

    public void setLastDisbursementDate(LocalDate lastDisbursementDate) {
        this.lastDisbursementDate = lastDisbursementDate;
    }

    public Integer getAvailablePeriod() {
        return availablePeriod;
    }

    public void setAvailablePeriod(Integer availablePeriod) {
        this.availablePeriod = availablePeriod;
    }

    public Integer getTotalUniversityScholarships() {
        return totalUniversityScholarships;
    }

    public void setTotalUniversityScholarships(Integer totalUniversityScholarships) {
        this.totalUniversityScholarships = totalUniversityScholarships;
    }

    public java.util.List<UniversityScholarshipFundRequestDto> getFundRequests() {
        return fundRequests;
    }

    public void setFundRequests(java.util.List<UniversityScholarshipFundRequestDto> fundRequests) {
        this.fundRequests = fundRequests;
    }

    public String getApprovalListId() {
        return approvalListId;
    }

    public void setApprovalListId(String approvalListId) {
        this.approvalListId = approvalListId;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getScannedReportPath() {
        return scannedReportPath;
    }

    public void setScannedReportPath(String scannedReportPath) {
        this.scannedReportPath = scannedReportPath;
    }

    public Boolean getFollowDeviationProcess() {
        return followDeviationProcess;
    }

    public void setFollowDeviationProcess(Boolean followDeviationProcess) {
        this.followDeviationProcess = followDeviationProcess;
    }

    public String getSubmissionLocation() {
        return submissionLocation;
    }

    public void setSubmissionLocation(String submissionLocation) {
        this.submissionLocation = submissionLocation;
    }
}

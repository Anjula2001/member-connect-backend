package com.memberconnect.backend.dto;

import java.time.LocalDate;

/**
 * One row of the University Scholarship Fund Requests list (MMS).
 *
 * Distinct from UniversityScholarshipFundRequestDto, which describes a fund request on
 * its own. The list screen also shows and searches the member and the student, which
 * live on the parent scholarship request, so those are flattened onto the row here.
 *
 * The screen used to do this flattening itself, in the browser, after downloading every
 * scholarship request with its fund requests nested inside - which is why it could not
 * filter server-side.
 */
public class UniversityScholarshipFundRequestListDto {

    private Long id;
    private String requestId;
    private String scholarshipRequestId;
    private LocalDate requestedDate;
    private String requestedPeriod;
    private Double requestedAmount;
    private Double disbursedAmount;
    private String status;

    // Carried from the parent scholarship request.
    private String memberId;
    private String memberName;
    private String studentName;
    private String universityName;
    private String nic;
    private String submissionLocation;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getScholarshipRequestId() {
        return scholarshipRequestId;
    }

    public void setScholarshipRequestId(String scholarshipRequestId) {
        this.scholarshipRequestId = scholarshipRequestId;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(LocalDate requestedDate) {
        this.requestedDate = requestedDate;
    }

    public String getRequestedPeriod() {
        return requestedPeriod;
    }

    public void setRequestedPeriod(String requestedPeriod) {
        this.requestedPeriod = requestedPeriod;
    }

    public Double getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(Double requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public Double getDisbursedAmount() {
        return disbursedAmount;
    }

    public void setDisbursedAmount(Double disbursedAmount) {
        this.disbursedAmount = disbursedAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getUniversityName() {
        return universityName;
    }

    public void setUniversityName(String universityName) {
        this.universityName = universityName;
    }

    public String getNic() {
        return nic;
    }

    public void setNic(String nic) {
        this.nic = nic;
    }

    public String getSubmissionLocation() {
        return submissionLocation;
    }

    public void setSubmissionLocation(String submissionLocation) {
        this.submissionLocation = submissionLocation;
    }
}

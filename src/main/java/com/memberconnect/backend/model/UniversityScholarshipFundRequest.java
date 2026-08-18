package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.UniversityScholarshipFundRequestStatus;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "University_Scholarship_Fund_Request")
public class UniversityScholarshipFundRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String fundRequestId;

    @ManyToOne
    @JoinColumn(name = "university_scholarship_request_id")
    private UniversityScholarshipRequest universityScholarshipRequest;

    @Column(name = "RequestedDate")
    private LocalDate requestedDate;

    @Column(name = "RequestedPeriod")
    private String requestedPeriod;

    @Column(name = "RequestedAmount")
    private Double requestedAmount;

    @Column(name = "DisbursedAmount")
    private Double disbursedAmount;

    @Column(name = "DisbursementDate")
    private LocalDate disbursementDate;

    @Enumerated(EnumType.STRING)
    private UniversityScholarshipFundRequestStatus status;

    @Column(name = "IncompleteReason")
    private String incompleteReason;

    @Column(name = "DecisionReason")
    private String decisionReason;

    public Long getId() {
        return id;
    }

    public String getFundRequestId() {
        return fundRequestId;
    }

    public void setFundRequestId(String fundRequestId) {
        this.fundRequestId = fundRequestId;
    }

    public UniversityScholarshipRequest getUniversityScholarshipRequest() {
        return universityScholarshipRequest;
    }

    public void setUniversityScholarshipRequest(UniversityScholarshipRequest universityScholarshipRequest) {
        this.universityScholarshipRequest = universityScholarshipRequest;
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

    public LocalDate getDisbursementDate() {
        return disbursementDate;
    }

    public void setDisbursementDate(LocalDate disbursementDate) {
        this.disbursementDate = disbursementDate;
    }

    public UniversityScholarshipFundRequestStatus getStatus() {
        return status;
    }

    public void setStatus(UniversityScholarshipFundRequestStatus status) {
        this.status = status;
    }

    public String getIncompleteReason() {
        return incompleteReason;
    }

    public void setIncompleteReason(String incompleteReason) {
        this.incompleteReason = incompleteReason;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }
}

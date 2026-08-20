package com.memberconnect.backend.dto;

import java.time.LocalDate;

public class UniversityScholarshipFundRequestDto {
    private Long id;
    private String requestId;
    private String scholarshipRequestId;
    private LocalDate requestedDate;
    private String requestedPeriod;
    private Double requestedAmount;
    private Double disbursedAmount;
    private LocalDate disbursementDate;
    private String status;
    private String incompleteReason;
    private String decisionReason;
    private java.time.LocalDateTime financeIntegratedAt;
    private String financeIntegratedBy;

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

    public LocalDate getDisbursementDate() {
        return disbursementDate;
    }

    public void setDisbursementDate(LocalDate disbursementDate) {
        this.disbursementDate = disbursementDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
    public java.time.LocalDateTime getFinanceIntegratedAt() {
        return financeIntegratedAt;
    }

    public void setFinanceIntegratedAt(java.time.LocalDateTime financeIntegratedAt) {
        this.financeIntegratedAt = financeIntegratedAt;
    }

    public String getFinanceIntegratedBy() {
        return financeIntegratedBy;
    }

    public void setFinanceIntegratedBy(String financeIntegratedBy) {
        this.financeIntegratedBy = financeIntegratedBy;
    }
}

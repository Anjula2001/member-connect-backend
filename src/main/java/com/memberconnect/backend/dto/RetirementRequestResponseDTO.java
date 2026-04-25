package com.memberconnect.backend.dto;

public class RetirementRequestResponseDTO {

    private Long id;
    private String requestNo;
    private String memberId;
    private String requestedDate;
    private String effectiveDate;
    private String comment;
    private String status;
    private String incompleteReason;
    private String rejectReason;

    public RetirementRequestResponseDTO() {
    }

    public RetirementRequestResponseDTO(
            Long id,
            String requestNo,
            String memberId,
            String requestedDate,
            String effectiveDate,
            String comment,
            String status,
            String incompleteReason,
            String rejectReason
    ) {
        this.id = id;
        this.requestNo = requestNo;
        this.memberId = memberId;
        this.requestedDate = requestedDate;
        this.effectiveDate = effectiveDate;
        this.comment = comment;
        this.status = status;
        this.incompleteReason = incompleteReason;
        this.rejectReason = rejectReason;
    }

    public Long getId() {
        return id;
    }

    public String getRequestNo() {
        return requestNo;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getRequestedDate() {
        return requestedDate;
    }

    public String getEffectiveDate() {
        return effectiveDate;
    }

    public String getComment() {
        return comment;
    }

    public String getStatus() {
        return status;
    }

    public String getIncompleteReason() {
        return incompleteReason;
    }

    public String getRejectReason() {
        return rejectReason;
    }
}
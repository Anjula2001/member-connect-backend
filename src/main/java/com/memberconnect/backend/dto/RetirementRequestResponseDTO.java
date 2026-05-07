package com.memberconnect.backend.dto;

public class RetirementRequestResponseDTO {

    private Long id;
    private String requestNo;
    private String memberId;
    private String memberFullName;
    private String nameAsInPayroll;
    private String nameWithInitials;
    private String nic;
    private String requestedDate;
    private String effectiveDate;
    private String comment;
    private String status;
    private String incompleteReason;
    private String rejectReason;
    private boolean hasLoanBalance;
    private boolean hasIndirectObligations;

    public RetirementRequestResponseDTO() {}

    public RetirementRequestResponseDTO(
            Long id,
            String requestNo,
            String memberId,
            String memberFullName,
            String nameAsInPayroll,
            String nameWithInitials,
            String nic,
            String requestedDate,
            String effectiveDate,
            String comment,
            String status,
            String incompleteReason,
            String rejectReason,
            boolean hasLoanBalance,
            boolean hasIndirectObligations
    ) {
        this.id = id;
        this.requestNo = requestNo;
        this.memberId = memberId;
        this.memberFullName = memberFullName;
        this.nameAsInPayroll = nameAsInPayroll;
        this.nameWithInitials = nameWithInitials;
        this.nic = nic;
        this.requestedDate = requestedDate;
        this.effectiveDate = effectiveDate;
        this.comment = comment;
        this.status = status;
        this.incompleteReason = incompleteReason;
        this.rejectReason = rejectReason;
        this.hasLoanBalance = hasLoanBalance;
        this.hasIndirectObligations = hasIndirectObligations;
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

    public String getMemberFullName() {
        return memberFullName;
    }

    public String getNameAsInPayroll() {
        return nameAsInPayroll;
    }

    public String getNameWithInitials() {
        return nameWithInitials;
    }

    public String getNic() {
        return nic;
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

    public boolean isHasLoanBalance() {
        return hasLoanBalance;
    }

    public boolean isHasIndirectObligations() {
        return hasIndirectObligations;
    }
}

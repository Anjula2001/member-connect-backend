package com.memberconnect.backend.dto;

import java.util.List;

public class TerminationRequestResponseDTO {

    private Long id;
    private String requestNo;
    private String memberId;
    private String memberFullName;
    private String nameAsInPayroll;
    private String nameWithInitials;
    private String nic;
    private String terminationReasonId;
    private String terminationReason;
    private String requestedDate;
    private String effectiveDate;
    private String comment;
    private String status;
    private String incompleteReason;
    private String rejectReason;
    private boolean hasLoanBalance;
    private boolean hasIndirectObligations;
    private List<TerminationMinorDisbursementDTO> minorDisbursements;

    public TerminationRequestResponseDTO() {}

    public TerminationRequestResponseDTO(
            Long id,
            String requestNo,
            String memberId,
            String memberFullName,
            String nameAsInPayroll,
            String nameWithInitials,
            String nic,
            String terminationReasonId,
            String terminationReason,
            String requestedDate,
            String effectiveDate,
            String comment,
            String status,
            String incompleteReason,
            String rejectReason,
            boolean hasLoanBalance,
            boolean hasIndirectObligations,
            List<TerminationMinorDisbursementDTO> minorDisbursements
    ) {
        this.id = id;
        this.requestNo = requestNo;
        this.memberId = memberId;
        this.memberFullName = memberFullName;
        this.nameAsInPayroll = nameAsInPayroll;
        this.nameWithInitials = nameWithInitials;
        this.nic = nic;
        this.terminationReasonId = terminationReasonId;
        this.terminationReason = terminationReason;
        this.requestedDate = requestedDate;
        this.effectiveDate = effectiveDate;
        this.comment = comment;
        this.status = status;
        this.incompleteReason = incompleteReason;
        this.rejectReason = rejectReason;
        this.hasLoanBalance = hasLoanBalance;
        this.hasIndirectObligations = hasIndirectObligations;
        this.minorDisbursements = minorDisbursements;
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

    public String getTerminationReasonId() {
        return terminationReasonId;
    }

    public String getTerminationReason() {
        return terminationReason;
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

    public List<TerminationMinorDisbursementDTO> getMinorDisbursements() {
        return minorDisbursements;
    }
}

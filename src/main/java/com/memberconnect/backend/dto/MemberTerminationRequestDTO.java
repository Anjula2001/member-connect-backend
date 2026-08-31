package com.memberconnect.backend.dto;

import java.util.List;

public class MemberTerminationRequestDTO {

    // Id of the selected Termination Reasons Master row. Kept as a String at the
    // API boundary because an HTML <select> value is always a string; it is
    // parsed and resolved against the master server-side.
    private String terminationReasonId;

    // Accepted for backward compatibility only and never persisted: the stored
    // reason text always comes from the master row, not from the client.
    private String terminationReason;
    private String requestedDate;
    private String effectiveDate;
    private String comment;
    private List<TerminationMinorDisbursementDTO> minorDisbursements;

    public String getTerminationReasonId() {
        return terminationReasonId;
    }

    public void setTerminationReasonId(String terminationReasonId) {
        this.terminationReasonId = terminationReasonId;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
    }

    public String getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(String requestedDate) {
        this.requestedDate = requestedDate;
    }

    public String getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(String effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<TerminationMinorDisbursementDTO> getMinorDisbursements() {
        return minorDisbursements;
    }

    public void setMinorDisbursements(List<TerminationMinorDisbursementDTO> minorDisbursements) {
        this.minorDisbursements = minorDisbursements;
    }
}

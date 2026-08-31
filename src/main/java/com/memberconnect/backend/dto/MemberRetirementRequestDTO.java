package com.memberconnect.backend.dto;

public class MemberRetirementRequestDTO {

    private String requestedDate;
    private String effectiveDate;
    private String comment;

    public MemberRetirementRequestDTO() {}

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
}
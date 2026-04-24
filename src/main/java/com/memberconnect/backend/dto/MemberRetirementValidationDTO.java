package com.memberconnect.backend.dto;

public class MemberRetirementValidationDTO {

    private boolean hasOutstandingLoans;
    private boolean hasLoanObligations;
    private double totalOutstandingLoanBalance;
    private boolean canSubmit;
    private String message;

    public MemberRetirementValidationDTO() {
    }

    public boolean isHasOutstandingLoans() {
        return hasOutstandingLoans;
    }

    public void setHasOutstandingLoans(boolean hasOutstandingLoans) {
        this.hasOutstandingLoans = hasOutstandingLoans;
    }

    public boolean isHasLoanObligations() {
        return hasLoanObligations;
    }

    public void setHasLoanObligations(boolean hasLoanObligations) {
        this.hasLoanObligations = hasLoanObligations;
    }

    public double getTotalOutstandingLoanBalance() {
        return totalOutstandingLoanBalance;
    }

    public void setTotalOutstandingLoanBalance(double totalOutstandingLoanBalance) {
        this.totalOutstandingLoanBalance = totalOutstandingLoanBalance;
    }

    public boolean isCanSubmit() {
        return canSubmit;
    }

    public void setCanSubmit(boolean canSubmit) {
        this.canSubmit = canSubmit;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
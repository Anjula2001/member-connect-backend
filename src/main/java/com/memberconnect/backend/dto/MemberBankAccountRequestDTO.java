package com.memberconnect.backend.dto;

public class MemberBankAccountRequestDTO {

    private String bankId;
    private String branchId;
    private String accountNumber;

    public MemberBankAccountRequestDTO() {
    }

    public MemberBankAccountRequestDTO(String bankId, String branchId, String accountNumber) {
        this.bankId = bankId;
        this.branchId = branchId;
        this.accountNumber = accountNumber;
    }

    public String getBankId() {
        return bankId;
    }

    public void setBankId(String bankId) {
        this.bankId = bankId;
    }

    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

}
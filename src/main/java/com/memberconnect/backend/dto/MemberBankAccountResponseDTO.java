package com.memberconnect.backend.dto;

public class MemberBankAccountResponseDTO {

    private Long id;
    private String memberId;
    private String bankId;
    private String bankName;
    private String branchId;
    private String branchName;
    private String accountNumber;

    public MemberBankAccountResponseDTO() {}

    public MemberBankAccountResponseDTO(
            Long id,
            String memberId,
            String bankId,
            String bankName,
            String branchId,
            String branchName,
            String accountNumber
    ) {
        this.id = id;
        this.memberId = memberId;
        this.bankId = bankId;
        this.bankName = bankName;
        this.branchId = branchId;
        this.branchName = branchName;
        this.accountNumber = accountNumber;
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public String getMemberId() {
        return memberId;
    }
    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getBankId() {
        return bankId;
    }
    public void setBankId(String bankId) {
        this.bankId = bankId;
    }

    public String getBankName() {
        return bankName;
    }
    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBranchId() {
        return branchId;
    }
    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public String getBranchName() {
        return branchName;
    }
    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }
    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }
}
package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "MemberBankAccount")
public class MemberBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "MemberId", nullable = false)
    private String memberId;

    @Column(name = "BankId", nullable = false)
    private String bankId;

    @Column(name = "BranchId", nullable = false)
    private String branchId;

    @Column(name = "AccountNumber", nullable = false)
    private String accountNumber;

    public MemberBankAccount() {
    }

    public MemberBankAccount(String memberId, String bankId, String branchId, String accountNumber) {
        this.memberId = memberId;
        this.bankId = bankId;
        this.branchId = branchId;
        this.accountNumber = accountNumber;
    }

    public Long getId() {
        return id;
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
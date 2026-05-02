package com.memberconnect.backend.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "minor_savings_account")
public class MinorSavingsAccount {

    @Id
    @Column(name = "minor_account_no")
    private String minorAccountNo;

    @Column(name = "birth_certificate_no")
    private String birthCertificateNo;

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(name = "balance", nullable = false)
    private float balance;

    @Column(name = "remitted_amount")
    private Double remittedAmount;

    @Column(name = "remitted_date")
    private LocalDate remittedDate;

    public MinorSavingsAccount() {}

    public MinorSavingsAccount(String minorAccountNo, String memberId, String holderName, float balance) {
        this.minorAccountNo = minorAccountNo;
        this.memberId = memberId;
        this.holderName = holderName;
        this.balance = balance;
    }

    public String getMinorAccountNo() {
        return minorAccountNo;
    }

    public void setMinorAccountNo(String minorAccountNo) {
        this.minorAccountNo = minorAccountNo;
    }

    public String getBirthCertificateNo() {
        return birthCertificateNo;
    }

    public void setBirthCertificateNo(String birthCertificateNo) {
        this.birthCertificateNo = birthCertificateNo;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public float getBalance() {
        return balance;
    }

    public void setBalance(float balance) {
        this.balance = balance;
    }

    public Double getRemittedAmount() {
        return remittedAmount;
    }

    public void setRemittedAmount(Double remittedAmount) {
        this.remittedAmount = remittedAmount;
    }

    public LocalDate getRemittedDate() {
        return remittedDate;
    }

    public void setRemittedDate(LocalDate remittedDate) {
        this.remittedDate = remittedDate;
    }
}
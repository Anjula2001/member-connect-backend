package com.memberconnect.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "minor_accounts")
public class MinorAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "BirthCertificateNumber", nullable = false, unique = true)
    private String birthCertificateNumber;

    @Column(name = "RemittedMonths")
    private String remittedMonths;


    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @ManyToOne
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "created_date")
    private LocalDate createdDate;

    @Column(name = "is_active")
    private Boolean isActive;

    public MinorAccount() {}

    public MinorAccount(String birthCertificateNumber, String accountNumber,
                        Bank bank, Branch branch, LocalDate createdDate, Boolean isActive) {
        this.birthCertificateNumber = birthCertificateNumber;
      
        this.accountNumber = accountNumber;
        this.bank = bank;
        this.branch = branch;
        this.createdDate = createdDate;
        this.isActive = isActive;
    }
    
    // getters & setters
    public Long getId() {
        return id;
    }

    public String getBirthCertificateNumber() {
        return birthCertificateNumber;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Bank getBank() {
        return bank;
    }

    public Branch getBranch() {
        return branch;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public String getRemittedMonths() {
        return remittedMonths;
    }
}
package com.memberconnect.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "minor_accounts")
public class MinorAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bc_no", nullable = false)
    private String bcNo;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

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

    public MinorAccount(String bcNo, Member member, String accountNumber,
                        Bank bank, Branch branch, LocalDate createdDate, Boolean isActive) {
        this.bcNo = bcNo;
        this.member = member;
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

    public String getBcNo() {
        return bcNo;
    }

    public Member getMember() {
        return member;
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
}
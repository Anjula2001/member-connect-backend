package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "BankBranch")
public class BankBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false, unique = true)
    private String branchId;

    @Column(name = "bank_id", nullable = false)
    private String bankId;

    @Column(name = "name", nullable = false)
    private String name;

    public BankBranch() {
    }

    public BankBranch(String branchId, String bankId, String name) {
        this.branchId = branchId;
        this.bankId = bankId;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public String getBankId() {
        return bankId;
    }

    public void setBankId(String bankId) {
        this.bankId = bankId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
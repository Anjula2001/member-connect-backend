package com.memberconnect.backend.dto;

public class BankBranchDTO {

    private String branchId;
    private String name;

    // Default no-args constructor
    public BankBranchDTO() {}

    // All-args constructor
    public BankBranchDTO(String branchId, String name) {
        this.branchId = branchId;
        this.name = name;
    }

    // Getters and setters
    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
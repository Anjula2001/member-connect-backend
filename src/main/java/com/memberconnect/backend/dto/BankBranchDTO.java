package com.memberconnect.backend.dto;

public class BankBranchDTO {

    private String branchId;
    private String name;

    public BankBranchDTO() {}

    public BankBranchDTO(String branchId, String name) {
        this.branchId = branchId;
        this.name = name;
    }

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
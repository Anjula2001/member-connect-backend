package com.memberconnect.backend.dto;

public class BankDTO {

    private String bankId;
    private String name;

    public BankDTO() {
    }

    public BankDTO(String bankId, String name) {
        this.bankId = bankId;
        this.name = name;
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
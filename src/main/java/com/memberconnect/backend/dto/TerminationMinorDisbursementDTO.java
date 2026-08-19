package com.memberconnect.backend.dto;

public class TerminationMinorDisbursementDTO {

    private Long id;
    private String minorAccountNo;
    private String holderName;
    private Long disbursementBankId;
    private String disbursementBankName;
    private Long disbursementBranchId;
    private String disbursementBranchName;
    private String disbursementAccountNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMinorAccountNo() {
        return minorAccountNo;
    }

    public void setMinorAccountNo(String minorAccountNo) {
        this.minorAccountNo = minorAccountNo;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public Long getDisbursementBankId() {
        return disbursementBankId;
    }

    public void setDisbursementBankId(Long disbursementBankId) {
        this.disbursementBankId = disbursementBankId;
    }

    public String getDisbursementBankName() {
        return disbursementBankName;
    }

    public void setDisbursementBankName(String disbursementBankName) {
        this.disbursementBankName = disbursementBankName;
    }

    public Long getDisbursementBranchId() {
        return disbursementBranchId;
    }

    public void setDisbursementBranchId(Long disbursementBranchId) {
        this.disbursementBranchId = disbursementBranchId;
    }

    public String getDisbursementBranchName() {
        return disbursementBranchName;
    }

    public void setDisbursementBranchName(String disbursementBranchName) {
        this.disbursementBranchName = disbursementBranchName;
    }

    public String getDisbursementAccountNo() {
        return disbursementAccountNo;
    }

    public void setDisbursementAccountNo(String disbursementAccountNo) {
        this.disbursementAccountNo = disbursementAccountNo;
    }
}

package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "termination_minor_disbursement")
public class TerminationMinorDisbursement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "termination_request_id", nullable = false)
    private TerminationRequest terminationRequest;

    @Column(name = "minor_account_no", nullable = false)
    private String minorAccountNo;

    @Column(name = "minor_account_holder_name")
    private String minorAccountHolderName;

    @Column(name = "disbursement_bank")
    private String disbursementBank;

    @Column(name = "branch")
    private String branch;

    @Column(name = "disbursement_account_number")
    private String disbursementAccountNumber;

    public Long getId() {
        return id;
    }

    public TerminationRequest getTerminationRequest() {
        return terminationRequest;
    }

    public void setTerminationRequest(TerminationRequest terminationRequest) {
        this.terminationRequest = terminationRequest;
    }

    public String getMinorAccountNo() {
        return minorAccountNo;
    }

    public void setMinorAccountNo(String minorAccountNo) {
        this.minorAccountNo = minorAccountNo;
    }

    public String getMinorAccountHolderName() {
        return minorAccountHolderName;
    }

    public void setMinorAccountHolderName(String minorAccountHolderName) {
        this.minorAccountHolderName = minorAccountHolderName;
    }

    public String getDisbursementBank() {
        return disbursementBank;
    }

    public void setDisbursementBank(String disbursementBank) {
        this.disbursementBank = disbursementBank;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getDisbursementAccountNumber() {
        return disbursementAccountNumber;
    }

    public void setDisbursementAccountNumber(String disbursementAccountNumber) {
        this.disbursementAccountNumber = disbursementAccountNumber;
    }
}

package com.memberconnect.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "minor_account_remittance")
public class MinorAccountRemittance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minor_account_no", nullable = false)
    private String minorAccountNo;

    @Column(name = "remittance_month", nullable = false)
    private String remittanceMonth;

    @Column(name = "remittance_amount")
    private Double remittanceAmount;

    public MinorAccountRemittance() {}

    public MinorAccountRemittance(String minorAccountNo, String remittanceMonth, Double remittanceAmount) {
        this.minorAccountNo = minorAccountNo;
        this.remittanceMonth = remittanceMonth;
        this.remittanceAmount = remittanceAmount;
    }

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

    public String getRemittanceMonth() {
        return remittanceMonth;
    }

    public void setRemittanceMonth(String remittanceMonth) {
        this.remittanceMonth = remittanceMonth;
    }

    public Double getRemittanceAmount() {
        return remittanceAmount;
    }

    public void setRemittanceAmount(Double remittanceAmount) {
        this.remittanceAmount = remittanceAmount;
    }
}

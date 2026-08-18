package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.ApplicationStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "RemittanceAmountChange")
public class RemittanceAmountChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus newStatus;

    private String newRemittanceAmount;
    private String newRemittanceCurrency;
    private String remittanceAccountType;
    private String memberId;

    // --- 1. No-Args Constructor (Required by JPA) ---
    public RemittanceAmountChange() {
    }

    // --- 2. All-Args Constructor ---
    public RemittanceAmountChange(Integer id, ApplicationStatus newStatus,
                                  String newRemittanceAmount, String newRemittanceCurrency, String remittanceAccountType, String memberId) {
        this.id = id;
        this.newStatus = newStatus;
        this.newRemittanceAmount = newRemittanceAmount;
        this.newRemittanceCurrency = newRemittanceCurrency;
        this.remittanceAccountType = remittanceAccountType;
        this.memberId = memberId;
    }

    // --- 3. Getters and Setters ---

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public ApplicationStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(ApplicationStatus newStatus) {
        this.newStatus = newStatus;
    }

    public String getNewRemittanceAmount() {
        return newRemittanceAmount;
    }

    public void setNewRemittanceAmount(String newRemittanceAmount) {
        this.newRemittanceAmount = newRemittanceAmount;
    }

    public String getNewRemittanceCurrency() {
        return newRemittanceCurrency;
    }

    public void setNewRemittanceCurrency(String newRemittanceCurrency) {
        this.newRemittanceCurrency = newRemittanceCurrency;
    }

    public String getRemittanceAccountType() {
        return remittanceAccountType;
    }

    public void setRemittanceAccountType(String remittanceAccountType) {
        this.remittanceAccountType = remittanceAccountType;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }
}
package com.memberconnect.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "scholarship_remittance")
public class ScholarshipRemittance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "remittance_month", nullable = false)
    private String remittanceMonth;

    @Column(name = "remitted", nullable = false)
    private Boolean remitted;

    @Column(name = "remittance_amount")
    private Double remittanceAmount;

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public String getRemittanceMonth() {
        return remittanceMonth;
    }

    public void setRemittanceMonth(String remittanceMonth) {
        this.remittanceMonth = remittanceMonth;
    }

    public Boolean getRemitted() {
        return remitted;
    }

    public void setRemitted(Boolean remitted) {
        this.remitted = remitted;
    }

    public Double getRemittanceAmount() {
        return remittanceAmount;
    }

    public void setRemittanceAmount(Double remittanceAmount) {
        this.remittanceAmount = remittanceAmount;
    }
}
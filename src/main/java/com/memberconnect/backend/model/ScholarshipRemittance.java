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
    private String remittanceMonth; // format: yyyy-MM

    @Column(name = "remitted", nullable = false)
    private Boolean remitted;

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
}
package com.memberconnect.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "scholarship_month_settlement")
public class ScholarshipMonthSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "settlement_month", nullable = false)
    private String settlementMonth; // yyyy-MM

    @Column(name = "settled", nullable = false)
    private Boolean settled;

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public String getSettlementMonth() {
        return settlementMonth;
    }

    public void setSettlementMonth(String settlementMonth) {
        this.settlementMonth = settlementMonth;
    }

    public Boolean getSettled() {
        return settled;
    }

    public void setSettled(Boolean settled) {
        this.settled = settled;
    }
}
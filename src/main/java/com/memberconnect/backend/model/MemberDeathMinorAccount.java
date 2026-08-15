package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "member_death_minor_account")
public class MemberDeathMinorAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_death_record_id", nullable = false)
    private MemberDeathRecord record;

    @Column(name = "minor_account_number", length = 100)
    private String minorAccountNumber;

    @Column(name = "minor_account_holder_name", length = 200)
    private String minorAccountHolderName;

    @Column(name = "disbursement_bank", length = 100)
    private String disbursementBank;

    @Column(name = "branch", length = 100)
    private String branch;

    @Column(name = "disbursement_account_number", length = 100)
    private String disbursementAccountNumber;
}

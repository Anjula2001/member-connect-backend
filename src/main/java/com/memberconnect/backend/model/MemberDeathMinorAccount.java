package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "MemberDeathMinorAccount")
public class MemberDeathMinorAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_death_record_id", nullable = false)
    private MemberDeathRecord memberDeathRecord;

    @Column(length = 100)
    private String minorAccountNumber;

    @Column(length = 200)
    private String minorAccountHolderName;

    @Column(length = 100)
    private String disbursementBank;

    @Column(length = 100)
    private String branch;

    @Column(length = 100)
    private String disbursementAccountNumber;
}

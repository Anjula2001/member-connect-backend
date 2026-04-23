package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.MemberTransferRequestStatus;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Entity
@Table(name = "MemberTransferRequest")
public class MemberTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String MemberTransferID;

    @ManyToOne
    @JoinColumn(name = "MemberId", referencedColumnName = "memberId")
    private Member MemberId;

    @Enumerated(EnumType.STRING)
    private MemberTransferRequestStatus status;

    @Column(name = "NewDesignation")
    private String NewDesignation;

    @Column(name = "NewNatureofOccupation")
    private String NewNatureofOccupation;

    @Column(name = "NewWorkingLocationType")
    private String NewWorkingLocationType;

    @Column(name = "NewWorkingLocation")
    private String NewWorkingLocation;

    @Column(name = "NewEductionalZone")
    private String NewEductionalZone;

    @Column(name = "NewEducationalDistrict")
    private String NewEducationalDistrict;

    @Column(name = "NewComputerNo")
    private String NewComputerNo;

    @Column(name = "NewSalaryPayingOffice")
    private String NewSalaryPayingOffice;
}


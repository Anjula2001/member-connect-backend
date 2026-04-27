package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.Language;
import com.memberconnect.backend.enums.Gender;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "Member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "application_id", referencedColumnName = "id")
    private Member_Application application;

    @Column(unique = true)
    private String memberId;

    @Column(name = "MemberType")
    private String memberType;

    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    @Column(name = "MembershipStartDate")
    private LocalDate membershipStartDate;

    @Column(name = "Nic")
    private String nic;

    @Column(name = "Title")
    private String title;

    @Column(name = "FullName")
    private String fullName;

    @Column(name = "NameAsInPayroll")
    private String nameAsInPayroll;

    @Column(name = "NameWithInitials")
    private String nameWithInitials;

    @Column(name = "DateOfBirth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private Language preferredLanguage;

    @Column(name = "PermanentPrivateAddress")
    private String permanentPrivateAddress;

    @Column(name = "PrivateTelephone")
    private String privateTelephone;

    @Column(name = "MobileNumber")
    private String mobileNumber;

    @Column(name = "EmailAddress")
    private String emailAddress;

    @Column(name = "ComputerNoInPayslip")
    private String computerNoInPayslip;

    @Column(name = "SalaryPayingOffice")
    private String salaryPayingOffice;

    public Member() {}

    public Long getId() {
        return id;
    }
    
    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

}
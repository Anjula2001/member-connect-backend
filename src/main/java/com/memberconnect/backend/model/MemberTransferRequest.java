package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.MemberTransferStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "member_transfer_request")
public class MemberTransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", unique = true)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private MemberTransferStatus status;

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @ManyToOne
    @JoinColumn(name = "member_id", referencedColumnName = "memberId")
    private Member member;

    @ManyToOne
    @JoinColumn(name = "working_location_type_id")
    private WorkingLocationType newWorkingLocationType;

    @ManyToOne
    @JoinColumn(name = "educational_district_id")
    private EducationalDistrict newEducationalDistrict;

    @ManyToOne
    @JoinColumn(name = "educational_zone_id")
    private EducationalZone newEducationalZone;

    @ManyToOne
    @JoinColumn(name = "working_location_id")
    private WorkingLocation newWorkingLocation;

    @Column(name = "working_location_address")
    private String newWorkingLocationAddress;

    @Column(name = "salary_paying_office")
    private String newSalaryPayingOffice;

    @Column(name = "computer_no_in_payslip")
    private String newComputerNoInPayslip;

    @ManyToOne
    @JoinColumn(name = "designation_id")
    private Designation newDesignation;

    @ManyToOne
    @JoinColumn(name = "nature_of_occupation_id")
    private NatureOfOccupation newNatureOfOccupation;

    // Snapshot of member's current values at the time of request creation
    @Column(name = "current_designation")
    private String currentDesignation;

    @Column(name = "current_nature_of_occupation")
    private String currentNatureOfOccupation;

    @Column(name = "current_working_location_type")
    private String currentWorkingLocationType;

    @Column(name = "current_educational_district")
    private String currentEducationalDistrict;

    @Column(name = "current_educational_zone")
    private String currentEducationalZone;

    @Column(name = "current_working_location")
    private String currentWorkingLocation;

    @Column(name = "current_working_location_address")
    private String currentWorkingLocationAddress;

    @Column(name = "current_computer_no_in_payslip")
    private String currentComputerNoInPayslip;

    @Column(name = "current_salary_paying_office")
    private String currentSalaryPayingOffice;

    @Column(name = "decision_reason")
    private String decisionReason;

    @PrePersist
    protected void onCreate() {
        if (requestedDate == null) {
            requestedDate = LocalDate.now();
        }
    }
}
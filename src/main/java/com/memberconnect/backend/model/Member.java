package com.memberconnect.backend.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.memberconnect.backend.enums.Gender;
import com.memberconnect.backend.enums.Identification;
import com.memberconnect.backend.enums.Language;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.NatureOfOccupation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

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

    // The District Office branch this member registered/is administered through.
    // Distinct from educationalDistrict below (the member's working district).
    @Column(name = "SubmissionLocation")
    private String submissionLocation;

    @Column(name = "MembershipStartDate")
    private LocalDate membershipStartDate;

    // ---- Membership documentation printing (MR15-17) ----
    // Null means "not yet printed"; the print screens filter on these and the
    // reprint path is what allows a second copy once one is set.
    @Column(name = "MembershipCardPrintedAt")
    private LocalDateTime membershipCardPrintedAt;

    @Column(name = "SignatureCardPrintedAt")
    private LocalDateTime signatureCardPrintedAt;

    @Column(name = "PassbookPrintedAt")
    private LocalDateTime passbookPrintedAt;

    // ---- Membership documentation dispatch (MR18) ----
    // Denormalised from the dispatch record so the "Non-Dispatched Members"
    // filter does not need a join on every search.
    @Column(name = "DocumentsDispatchedAt")
    private LocalDateTime documentsDispatchedAt;

    @Column(name = "Nic")
    private String nic;

    @Column(name = "Title")
    private String title;

    @Column(name = "FullName")
    private String fullName;

    @Column(name = "NameAsInPayroll")
    private String nameAsInPayroll;

    @Column(name = "name_with_initials")
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


    @Column(name = "ProfilePictureUrl")
    private String profilePictureUrl;

    @Column(name = "SignatureUrl")
    private String signatureUrl;

    @Column(name = "WorkingLocationType")
    private String workingLocationType;

    @Column(name = "Designation")
    private String designation;

    @Enumerated(EnumType.STRING)
    private NatureOfOccupation natureOfOccupation;

    @Column(name = "EducationalDistrict")
    private String educationalDistrict; 

    @Column(name = "EducationalZone")
    private String educationalZone;

    @Column(name = "WorkingLocation")
    private String workingLocation;

    @Column(name = "WorkingLocationAddress")
    private String workingLocationAddress;

    @Column(name = "OfficeTelephone")
    private String officeTelephone;

    @Column(name = "NomineeFullName")
    private String nomineeFullName;

    @Column(name = "NomineeRelationship")
    private String nomineeRelationship;

    @Column(name = "NomineeAddress")
    private String nomineeAddress;

    @Enumerated(EnumType.STRING)
    private Identification identification;

    @Column(name = "IdentificationNumber")
    private String identificationNumber; //The Number and the Identification Type set in the Member Registration Application.

    @Column(name = "IdentificationDetails", length = 2000)
    private String identificationDetails;

    // Date the member's account was last updated/active. Used by the dormant
    // membership identification process.
    @Column(name = "last_activity_date")
    private LocalDate lastActivityDate;

    // Date the member was flagged as dormant (Dormant Selection Date).
    @Column(name = "dormant_selection_date")
    private LocalDate dormantSelectionDate;

    // ---- Temporary Scholarship finance eligibility (MMS23) --------------------
    //
    // Stand-ins for the Finance Module, which has not been delivered. Maintaining the
    // Remittance and Settlement tables by hand purely to answer "may this member apply
    // for a Scholarship?" is not practical, so the answer is recorded directly here and
    // set from the admin Member Accounts screen.
    //
    // The real month-by-month checks in UniversityScholarshipService are untouched and
    // still selected by scholarship.finance.validation.source=finance. These two fields
    // become dead weight once Finance is integrated, not a thing to migrate.
    //
    // Primitive boolean with a NOT NULL DEFAULT false column: an unassessed member is
    // not eligible, and there is no third "unknown" state to reason about.
    // columnDefinition carries the DEFAULT into the generated DDL. Without it,
    // ddl-auto=update emits a bare "add column ... not null", which Postgres refuses on
    // a table that already has rows - Hibernate logs that failure as a warning and
    // carries on, leaving the column absent and every Member select broken.
    @Column(name = "is_remittance", nullable = false,
            columnDefinition = "boolean not null default false")
    private boolean isRemittance;

    @Column(name = "is_settlement", nullable = false,
            columnDefinition = "boolean not null default false")
    private boolean isSettlement;

}
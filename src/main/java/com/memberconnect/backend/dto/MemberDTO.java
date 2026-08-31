package com.memberconnect.backend.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.Gender;
import com.memberconnect.backend.enums.Language;
import com.memberconnect.backend.enums.NatureOfOccupation;
import com.memberconnect.backend.enums.Identification;

@Data
public class MemberDTO {
  private Long id;
  private String memberId;
  private Long applicationId;   // FK to Member_Application — sent when creating from an approved application
  private String memberType;
  private MemberStatus status;
  private String submissionLocation;
  private LocalDate membershipStartDate;
  private String nic;
  private String title;
  private String fullName;
  private String nameAsInPayroll;
  private String nameWithInitials;
  private LocalDate dateOfBirth;
  private Gender gender;
  private Language preferredLanguage;
  private String permanentPrivateAddress;
  private String privateTelephone;
  private String mobileNumber;
  private String emailAddress;
  private String computerNoInPayslip;
  private String salaryPayingOffice;
  private String profilePictureUrl;
  private String signatureUrl;
  private String workingLocationType;
  private String designation;
  private NatureOfOccupation natureOfOccupation;
  private String educationalDistrict;
  private String educationalZone;
  private String workingLocation;
  private String workingLocationAddress;
  private String officeTelephone;
  private String nomineeFullName;
  private String nomineeRelationship;
  private String nomineeAddress;
  private Identification identification;
  private String identificationNumber;
  private String identificationDetails;

  /**
   * When each pre-printed document was last produced for this member, or null if it
   * never has been (MR15/MR16/MR17).
   *
   * These are read by the three Print screens, whose "Printed" column, row checkbox
   * and Re-print button all key off them. They were missing from this DTO while the
   * Member entity carried them and MembershipDocumentService stamped them, so the
   * column reported "Not printed" for every row — including members the same search
   * endpoint could already filter out via withoutDocument.
   */
  private LocalDateTime membershipCardPrintedAt;
  private LocalDateTime signatureCardPrintedAt;
  private LocalDateTime passbookPrintedAt;
}
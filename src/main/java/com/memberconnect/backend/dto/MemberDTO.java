package com.memberconnect.backend.dto;

import lombok.Data;
import java.time.LocalDate;
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
}

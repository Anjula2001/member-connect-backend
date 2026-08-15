package com.memberconnect.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class MemberDeathRecordDTO {
    private Long id;
    private String recordNo;
    private String memberId;
    private String memberFullName;
    private String memberNameWithInitials;
    private String memberNic;
    private String status;
    private String informedDate;
    private String deceasedDate;
    private Long causeOfDeathId;
    private String causeOfDeathName;
    private String comment;
    private String concernsIdentified;
    private String nomineeFullName;
    private String nomineeRelationship;
    private String nomineeAddress;
    private String nomineeIdentificationType;
    private String nomineeIdentificationNumber;
    private String nomineeMobile;
    private String nomineeEmail;
    private Long nomineeBankId;
    private String nomineeBankName;
    private Long nomineeBranchId;
    private String nomineeBranchName;
    private String nomineeAccountNo;
    private BigDecimal deathDonationAmount;
    private String incompleteReason;
    private String rejectReason;
    private boolean editable;
    private boolean submittable;
    private boolean hasLoanBalance;
    private boolean hasIndirectObligations;
    private List<MemberDeathMinorDisbursementDTO> minorDisbursements = new ArrayList<>();
    private List<MemberDeathDocumentDTO> documents = new ArrayList<>();
}

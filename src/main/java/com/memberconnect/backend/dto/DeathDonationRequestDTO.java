package com.memberconnect.backend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DeathDonationRequestDTO {

    private Long id;
    private String requestNo;
    private String memberId;
    private String memberFullName;
    private String memberNameWithInitials;
    private String memberNameAsInPayroll;
    private String memberNic;
    private String memberWorkingLocation;
    private String memberEducationalDistrict;
    private String status;
    private String relationshipToDeceased;
    private String requestedDate;
    private boolean deceasedMember;
    private String deceasedMemberId;
    private String deceasedName;
    private String maidenNameIfMarried;
    private String deceasedDate;
    private String deathCertificateNumber;
    private String deceasedPlaceOfWork;
    private String concernsIdentified;
    private String incompleteReason;
    private String rejectReason;
    private boolean dateRangeWarning;
    private List<DeathDonationRelativeDTO> relatives = new ArrayList<>();
}

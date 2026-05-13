package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.DeathRecordStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class MemberDeathResponseDTO {

    private Long id;
    private String recordId;
    
    private Long memberId;
    private String memberName; // extra useful field
    private String memberNic; // extra useful field

    private LocalDate informedDate;
    private LocalDate deceasedDate;
    private String causeOfDeath;
    private String comment;
    private String concernsIdentified;

    private String nomineeFullName;
    private String nomineeAddress;
    private String nomineeRelationship;
    private String nomineeIdentificationTypeAndNumber;
    private String nomineeMobileNo;
    private String nomineeEmailAddress;

    private String bank;
    private String bankBranch;
    private String accountNumber;

    private DeathRecordStatus status;
    private String incompleteReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<CreateMemberDeathDTO.MinorAccountDTO> minorAccounts;
    private List<CreateMemberDeathDTO.DocumentDTO> documents;
}

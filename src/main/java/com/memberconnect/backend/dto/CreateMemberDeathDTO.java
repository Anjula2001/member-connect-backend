package com.memberconnect.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CreateMemberDeathDTO {

    @NotNull(message = "Member ID is required")
    private Long memberId;

    @NotNull(message = "Informed Date is required")
    private LocalDate informedDate;

    @NotNull(message = "Deceased Date is required")
    private LocalDate deceasedDate;

    @NotBlank(message = "Cause of Death is required")
    private String causeOfDeath;

    private String comment;
    private String concernsIdentified;

    // Nominee Details
    private String nomineeFullName;
    private String nomineeAddress;
    private String nomineeRelationship;
    private String nomineeIdentificationTypeAndNumber;
    
    @NotBlank(message = "Nominee Mobile No is required")
    private String nomineeMobileNo;
    
    private String nomineeEmailAddress;

    // Bank Details
    @NotBlank(message = "Bank is required")
    private String bank;

    @NotBlank(message = "Bank Branch is required")
    private String bankBranch;

    @NotBlank(message = "Account Number is required")
    private String accountNumber;

    // Related items
    private List<MinorAccountDTO> minorAccounts;
    private List<DocumentDTO> documents;

    @Getter
    @Setter
    public static class MinorAccountDTO {
        private String minorAccountNumber;
        private String minorAccountHolderName;
        private String disbursementBank;
        private String branch;
        private String disbursementAccountNumber;
    }

    @Getter
    @Setter
    public static class DocumentDTO {
        private String documentType;
        private String fileName;
        private String mimeType;
        private Boolean mandatory;
    }
}

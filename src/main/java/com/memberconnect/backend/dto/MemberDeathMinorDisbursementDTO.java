package com.memberconnect.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberDeathMinorDisbursementDTO {
    private Long id;
    private String minorAccountNo;
    private String holderName;
    private Long disbursementBankId;
    private String disbursementBankName;
    private Long disbursementBranchId;
    private String disbursementBranchName;
    private String disbursementAccountNo;
}

package com.memberconnect.backend.dto;

import lombok.Data;

import java.util.List;

/** Backs the Remittance & Savings tab and the admin entry screen. */
@Data
public class MemberFinancialsDTO {
    private Long memberId;
    private String memberCode;
    private String memberName;
    private List<MemberRemittanceDTO> remittances;
    private List<MemberAccountDTO> accounts;
    /** True while no operative account has been synced from Finance. */
    private Boolean awaitingFinanceIntegration;
}

package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.ApplicationStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

@Data
public class RemittanceAmountChangeDTO {
    @Enumerated(EnumType.STRING)
    private ApplicationStatus newStatus;
    private Integer id;
    private String newRemittanceAmount;
    private String newRemittanceCurrency;
    private String remittanceAccountType;
    private String memberId;
}

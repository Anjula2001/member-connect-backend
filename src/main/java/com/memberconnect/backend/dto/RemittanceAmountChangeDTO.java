package com.memberconnect.backend.dto;

import lombok.Data;

@Data
public class RemittanceAmountChangeDTO {
    private Integer id;
    private String newRemittanceAmount;
    private String newRemittanceCurrency;
}

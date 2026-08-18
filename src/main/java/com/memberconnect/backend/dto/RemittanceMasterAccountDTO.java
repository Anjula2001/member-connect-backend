package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.RemittanceAccountCode;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RemittanceMasterAccountDTO {
    private Long id;
    private RemittanceAccountCode accountCode;
    private String accountName;
    private BigDecimal fixedAmount;
    private BigDecimal minimumAmount;
    private Boolean mandatory;
    private Integer displayOrder;
    private Boolean active;
}

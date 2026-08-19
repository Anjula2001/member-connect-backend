package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.AccountDataSource;
import com.memberconnect.backend.enums.RemittanceAccountCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MemberAccountDTO {
    private Long id;
    private RemittanceAccountCode accountCode;
    private String accountName;
    private String accountNumber;
    private BigDecimal balance;
    private LocalDate openedDate;
    private AccountDataSource source;
    private LocalDateTime lastSyncedAt;
}

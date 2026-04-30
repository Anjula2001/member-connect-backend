package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.ApplicationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Entity

public class RemittanceAmountChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Enumerated(EnumType.STRING)
    private ApplicationStatus newStatus;
    private String newRemittanceAmount;
    private String newRemittanceCurrency;
}

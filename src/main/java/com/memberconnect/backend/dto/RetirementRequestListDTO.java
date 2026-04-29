package com.memberconnect.backend.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetirementRequestListDTO {

    private Long id;
    private String requestNo;
    private String memberId;
    private String memberFullName;
    private String nameWithInitials;
    private String nic;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate requestedDate;

    private String status;
    private Boolean hasOutstandingLoans;
    private Boolean hasLoanObligations;
    private String location;

}
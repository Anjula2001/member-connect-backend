package com.memberconnect.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Grade5RequestApprovalDetailDTO {
    private String requestNo;
    private String status;
    private String rejectReason;
}

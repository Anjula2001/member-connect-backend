package com.memberconnect.backend.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class Grade5ScholarshipApprovalListDTO {
    private Long id;
    private String listId;
    private Long boardMeetingId;
    private LocalDate boardMeetingDate;
    private LocalDate actualMeetingDate;
    private String status;
    private String type;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private String processedBy;
    private String scannedReportPath;
    private String decision;
    private String boardRemarks;
    private List<String> requestNos; // attached requests
    private List<Grade5RequestApprovalDetailDTO> requestDetails; // processing decisions
}


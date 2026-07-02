package com.memberconnect.backend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TerminationApprovalListDTO {

    private Long id;
    private String listId;
    private Long boardMeetingId;
    private LocalDate boardMeetingDate;
    private List<String> requestNos = new ArrayList<>();
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private String processedBy;
    private LocalDate actualMeetingDate;
    private String decision;
    private String rejectReason;
    private String boardRemarks;
}

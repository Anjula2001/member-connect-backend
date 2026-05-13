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
    private String boardMeetingDate;
    private List<String> terminationIds = new ArrayList<>();
    private String status;
    private String createdAt;
    private String processedAt;
    private String processedBy;
    private String actualMeetingDate;
    private String decision;
    private String rejectReason;
    private String boardRemarks;
}

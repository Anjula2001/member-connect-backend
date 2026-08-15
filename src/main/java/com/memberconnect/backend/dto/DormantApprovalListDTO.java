package com.memberconnect.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DormantApprovalListDTO {
    private Long id;
    private String listId;
    private Long boardMeetingId;
    private LocalDate boardMeetingDate;
    private LocalDate actualMeetingDate;
    private List<String> memberIds = new ArrayList<>();
    private List<DormantMemberDTO> members = new ArrayList<>();
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private String processedBy;
    private String decision;
    private String rejectReason;
    private String boardRemarks;
    private LocalDateTime inactivatedAt;
}

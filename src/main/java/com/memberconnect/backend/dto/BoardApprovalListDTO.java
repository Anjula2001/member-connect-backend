package com.memberconnect.backend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class BoardApprovalListDTO {

	private Long id;
	private String listId;
	private Long boardMeetingId;
	private LocalDate boardMeetingDate;
	private List<String> applicationIds = new ArrayList<>();
	private List<Integer> nameChangeRequestIds = new ArrayList<>();
	private List<Integer> nomineeChangeRequestIds = new ArrayList<>();
	private String status;
	private LocalDateTime createdAt;
	private LocalDateTime processedAt;
	private String processedBy;
	private LocalDate actualMeetingDate;
	private String decision;
	private String rejectReason;
	private String boardRemarks;
}

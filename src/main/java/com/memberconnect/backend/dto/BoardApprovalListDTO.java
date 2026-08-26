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

	/**
	 * How many applications the list holds.
	 *
	 * The list view returns this WITHOUT applicationIds: the panel only ever renders
	 * the number, and producing the ids meant walking a lazy collection per row. Open
	 * a single list and applicationIds is populated as before.
	 */
	private Integer applicationCount;
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
	private String approvedListDocument;

	/**
	 * Per-request decisions for the Name and Nominee change requests on this list
	 * (MMC12 / MMC25). Empty means "approve everything", which is the screen's default.
	 */
	private List<ProfileChangeItemDecisionDTO> nameChangeDecisions = new ArrayList<>();
	private List<ProfileChangeItemDecisionDTO> nomineeChangeDecisions = new ArrayList<>();
}

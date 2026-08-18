package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.BoardApprovalListDTO;
import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.model.BoardApprovalList;
import com.memberconnect.backend.model.Member_Application;
import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.repository.BoardApprovalListRepository;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.MemberApplicationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@SuppressWarnings("null")
public class BoardApprovalListService {

	@Autowired
	private BoardApprovalListRepository boardApprovalListRepository;

	@Autowired
	private BoardmeetingRepository boardMeetingRepository;

	@Autowired
	private MemberApplicationRepository memberApplicationRepository;

	private BoardApprovalListDTO toDto(BoardApprovalList entity) {
		BoardApprovalListDTO dto = new BoardApprovalListDTO();
		dto.setId(entity.getId());
		dto.setListId(entity.getListId());
		dto.setBoardMeetingId(entity.getBoardMeetingId());
		dto.setBoardMeetingDate(entity.getBoardMeetingDate());
		dto.setApplicationIds(entity.getApplications().stream().map(Member_Application::getApplicationID).collect(Collectors.toList()));
		dto.setStatus(entity.getStatus());
		dto.setCreatedAt(entity.getCreatedAt());
		dto.setProcessedAt(entity.getProcessedAt());
		dto.setProcessedBy(entity.getProcessedBy());
		dto.setActualMeetingDate(entity.getActualMeetingDate());
		dto.setDecision(entity.getDecision());
		dto.setRejectReason(entity.getRejectReason());
		dto.setBoardRemarks(entity.getBoardRemarks());
		dto.setApprovedListDocument(entity.getApprovedListDocument());
		return dto;
	}

	public BoardApprovalListDTO createBoardApprovalList(BoardApprovalListDTO dto) {
		if (dto.getApplicationIds() == null || dto.getApplicationIds().isEmpty()) {
			throw new RuntimeException("No applications selected for board approval list");
		}

		if (dto.getBoardMeetingId() == null) {
			throw new RuntimeException("Board meeting is required");
		}

		BoardMeeting boardMeeting = boardMeetingRepository.findById(dto.getBoardMeetingId())
				.orElseThrow(() -> new RuntimeException("Board Meeting not found"));

		BoardApprovalList entity = new BoardApprovalList();
		entity.setListId("BAL-" + System.currentTimeMillis());
		entity.setBoardMeetingId(boardMeeting.getId());
		entity.setBoardMeetingDate(boardMeeting.getScheduledDate());
		entity.setStatus("CREATED");
		entity.setCreatedAt(LocalDateTime.now());

		for (String applicationId : dto.getApplicationIds()) {
			Member_Application application = memberApplicationRepository.findByApplicationID(applicationId)
					.orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));
			application.setStatus(ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST);
			memberApplicationRepository.save(application);
			entity.getApplications().add(application);
		}

		BoardApprovalList saved = boardApprovalListRepository.save(entity);

		return toDto(saved);
	}

	public List<BoardApprovalListDTO> getAllBoardApprovalLists() {
		return boardApprovalListRepository.findAll().stream()
				.map(this::toDto)
				.toList();
	}

	public BoardApprovalListDTO getBoardApprovalListByListId(String listId) {
		BoardApprovalList entity = boardApprovalListRepository.findByListId(listId)
				.orElseThrow(() -> new RuntimeException("Board approval list not found"));
		return toDto(entity);
	}

	public List<MemberApplicationDTO> getApplicationsByListId(String listId) {
		BoardApprovalList entity = boardApprovalListRepository.findByListId(listId)
				.orElseThrow(() -> new RuntimeException("Board approval list not found"));

		if (entity.getApplications().isEmpty()) {
			return Collections.emptyList();
		}

		return entity.getApplications().stream()
				.map(application -> {
					MemberApplicationDTO applicationDTO = new MemberApplicationDTO();
					applicationDTO.setId(application.getId());
					applicationDTO.setApplicationID(application.getApplicationID());
					applicationDTO.setStatus(application.getStatus());
					applicationDTO.setSubmissionLocation(application.getSubmissionLocation());
					applicationDTO.setTitle(application.getTitle());
					applicationDTO.setFullName(application.getFullName());
					applicationDTO.setApplicationDate(application.getApplicationDate());
					applicationDTO.setNameAsInPayroll(application.getNameAsInPayroll());
					applicationDTO.setNameWithInitials(application.getNameWithInitials());
					applicationDTO.setNicNumber(application.getNicNumber());
					applicationDTO.setDateOfBirth(application.getDateOfBirth());
					applicationDTO.setGender(application.getGender());
					applicationDTO.setPreferredLanguage(application.getPreferredLanguage());
					applicationDTO.setPermanentPrivateAddress(application.getPermanentPrivateAddress());
					applicationDTO.setWorkingLocationType(application.getWorkingLocationType());
					applicationDTO.setDesignation(application.getDesignation());
					applicationDTO.setNatureOfOccupation(application.getNatureOfOccupation());
					applicationDTO.setEducationalDistrict(application.getEducationalDistrict());
					applicationDTO.setEducationalZone(application.getEducationalZone());
					applicationDTO.setWorkingLocation(application.getWorkingLocation());
					applicationDTO.setWorkingLocationAddress(application.getWorkingLocationAddress());
					applicationDTO.setComputerNoInPayslip(application.getComputerNoInPayslip());
					applicationDTO.setSalaryPayingOffice(application.getSalaryPayingOffice());
					applicationDTO.setOfficeTelephone(application.getOfficeTelephone());
					applicationDTO.setPrivateTelephone(application.getPrivateTelephone());
					applicationDTO.setMobileNumber(application.getMobileNumber());
					applicationDTO.setEmailAddress(application.getEmailAddress());
					applicationDTO.setNomineeFullName(application.getNomineeFullName());
					applicationDTO.setNomineeRelationship(application.getNomineeRelationship());
					applicationDTO.setIdentification(application.getIdentification());
					applicationDTO.setIdentificationNumber(application.getIdentificationNumber());
					applicationDTO.setIdentificationDetails(application.getIdentificationDetails());
					applicationDTO.setNomineeAddress(application.getNomineeAddress());
					applicationDTO.setShareAccountAmount(application.getShareAccountAmount());
					applicationDTO.setSpecialDepositAmount(application.getSpecialDepositAmount());
					applicationDTO.setFixedDepositAmount(application.getFixedDepositAmount());
					applicationDTO.setScholarshipDeathDonationPensionAmount(application.getScholarshipDeathDonationPensionAmount());
					applicationDTO.setRejoinFlag(application.getRejoinFlag());
					writeBoardDecisionReason(applicationDTO, application.getBoardDecisionReason());
					return applicationDTO;
				})
				.collect(Collectors.toList());
	}

	private void writeBoardDecisionReason(MemberApplicationDTO dto, String boardDecisionReason) {
		try {
			Field field = MemberApplicationDTO.class.getDeclaredField("boardDecisionReason");
			field.setAccessible(true);
			field.set(dto, boardDecisionReason);
		} catch (ReflectiveOperationException error) {
			// Ignore if the field is unavailable; the rest of the payload is still valid.
		}
	}

	public BoardApprovalListDTO processBoardApprovalList(String listId, BoardApprovalListDTO dto) {
		BoardApprovalList entity = boardApprovalListRepository.findByListId(listId)
				.orElseThrow(() -> new RuntimeException("Board approval list not found"));

		if (dto.getDecision() == null || dto.getDecision().trim().isEmpty()) {
			throw new RuntimeException("Decision is required");
		}

		String decision = dto.getDecision().trim();
		boolean approved = "APPROVE".equalsIgnoreCase(decision);
		boolean rejected = "REJECT".equalsIgnoreCase(decision);
		if (!approved && !rejected) {
			throw new RuntimeException("Decision must be Approve or Reject");
		}

		if (rejected && (dto.getRejectReason() == null || dto.getRejectReason().trim().isEmpty())) {
			throw new RuntimeException("Reject reason is required when rejecting applications");
		}

		LocalDate actualMeetingDate = dto.getActualMeetingDate() != null ? dto.getActualMeetingDate() : LocalDate.now();
		String boardRemarks = dto.getBoardRemarks();
		String rejectReason = rejected ? dto.getRejectReason().trim() : null;

		// Note: We deliberately DO NOT update individual Member_Application statuses here.
		// The frontend handles per-application Approve/Reject updates via `updateMemberApplicationPartial`.

		entity.setStatus("PROCESSED");
		entity.setProcessedAt(LocalDateTime.now());
		entity.setProcessedBy(dto.getProcessedBy() == null || dto.getProcessedBy().trim().isEmpty()
				? "Head Office User"
				: dto.getProcessedBy().trim());
		entity.setActualMeetingDate(actualMeetingDate);
		entity.setDecision(approved ? "Approve" : "Reject");
		entity.setRejectReason(rejectReason);
		entity.setBoardRemarks(boardRemarks);
		// Scanned copy of the signed board approval sheet, if one was attached.
		if (dto.getApprovedListDocument() != null) {
			entity.setApprovedListDocument(dto.getApprovedListDocument());
		}

		BoardApprovalList saved = boardApprovalListRepository.save(entity);
		return toDto(saved);
	}

	public String deleteBoardApprovalList(String listId) {
		BoardApprovalList entity = boardApprovalListRepository.findByListId(listId)
				.orElseThrow(() -> new RuntimeException("Board approval list not found"));

		for (Member_Application application : entity.getApplications()) {
			if (application.getStatus() == ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST) {
				application.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
				memberApplicationRepository.save(application);
			}
		}

		boardApprovalListRepository.delete(entity);
		return "Board approval list deleted successfully";
	}
}

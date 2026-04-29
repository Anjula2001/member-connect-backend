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

import java.time.LocalDateTime;
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
		dto.setApplicationIds(parseApplicationIds(entity.getApplicationIdsCsv()));
		dto.setStatus(entity.getStatus());
		dto.setCreatedAt(entity.getCreatedAt());
		dto.setProcessedAt(entity.getProcessedAt());
		dto.setProcessedBy(entity.getProcessedBy());
		return dto;
	}

	private List<String> parseApplicationIds(String csv) {
		if (csv == null || csv.trim().isEmpty()) {
			return Collections.emptyList();
		}
		return Arrays.stream(csv.split(","))
				.map(String::trim)
				.filter(value -> !value.isEmpty())
				.collect(Collectors.toList());
	}

	private String serializeApplicationIds(List<String> applicationIds) {
		return String.join(",", applicationIds);
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
		entity.setApplicationIdsCsv(serializeApplicationIds(dto.getApplicationIds()));
		entity.setStatus("CREATED");
		entity.setCreatedAt(LocalDateTime.now());

		BoardApprovalList saved = boardApprovalListRepository.save(entity);

		for (String applicationId : dto.getApplicationIds()) {
			Member_Application application = memberApplicationRepository.findByApplicationID(applicationId)
					.orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));
			application.setStatus(ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST);
			memberApplicationRepository.save(application);
		}

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

		List<String> applicationIds = parseApplicationIds(entity.getApplicationIdsCsv());
		if (applicationIds.isEmpty()) {
			return Collections.emptyList();
		}

		return applicationIds.stream()
				.map(applicationId -> memberApplicationRepository.findByApplicationID(applicationId)
						.map(application -> {
							MemberApplicationDTO applicationDTO = new MemberApplicationDTO();
							applicationDTO.setId(application.getId());
							applicationDTO.setApplicationID(application.getApplicationID());
							applicationDTO.setStatus(application.getStatus());
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
							return applicationDTO;
						})
						.orElseThrow(() -> new RuntimeException("Application not found: " + applicationId)))
				.collect(Collectors.toList());
	}

	public String deleteBoardApprovalList(String listId) {
		BoardApprovalList entity = boardApprovalListRepository.findByListId(listId)
				.orElseThrow(() -> new RuntimeException("Board approval list not found"));

		List<String> applicationIds = parseApplicationIds(entity.getApplicationIdsCsv());
		for (String applicationId : applicationIds) {
			memberApplicationRepository.findByApplicationID(applicationId).ifPresent(application -> {
				if (application.getStatus() == ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST) {
					application.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
					memberApplicationRepository.save(application);
				}
			});
		}

		boardApprovalListRepository.delete(entity);
		return "Board approval list deleted successfully";
	}
}

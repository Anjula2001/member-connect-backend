package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.BoardApprovalListDTO;
import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.dto.NameChangeRequestDTO;
import com.memberconnect.backend.dto.NommineChangeRequestDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.model.BoardApprovalList;
import com.memberconnect.backend.model.Member_Application;
import com.memberconnect.backend.model.NameChangeRequest;
import com.memberconnect.backend.model.NommineChangeRequests;
import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.repository.BoardApprovalListRepository;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.MemberApplicationRepository;
import com.memberconnect.backend.repository.NameChangeRequestRepo;
import com.memberconnect.backend.repository.NominneChangeRequestRepo;
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

	@Autowired
	private NameChangeRequestRepo nameChangeRequestRepo;

	@Autowired
	private NominneChangeRequestRepo nomineeChangeRequestRepo;

	// ── CSV helpers ──────────────────────────────────────────────────────

	private List<String> parseCsv(String csv) {
		if (csv == null || csv.trim().isEmpty()) {
			return Collections.emptyList();
		}
		return Arrays.stream(csv.split(","))
				.map(String::trim)
				.filter(value -> !value.isEmpty())
				.collect(Collectors.toList());
	}

	private List<Integer> parseCsvAsIntegers(String csv) {
		if (csv == null || csv.trim().isEmpty()) {
			return Collections.emptyList();
		}
		return Arrays.stream(csv.split(","))
				.map(String::trim)
				.filter(value -> !value.isEmpty())
				.map(Integer::parseInt)
				.collect(Collectors.toList());
	}

	private String serializeStringIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) return null;
		return String.join(",", ids);
	}

	private String serializeIntegerIds(List<Integer> ids) {
		if (ids == null || ids.isEmpty()) return null;
		return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	// ── toDto ────────────────────────────────────────────────────────────

	private BoardApprovalListDTO toDto(BoardApprovalList entity) {
		BoardApprovalListDTO dto = new BoardApprovalListDTO();
		dto.setId(entity.getId());
		dto.setListId(entity.getListId());
		dto.setBoardMeetingId(entity.getBoardMeetingId());
		dto.setBoardMeetingDate(entity.getBoardMeetingDate());
		dto.setApplicationIds(entity.getApplications().stream().map(Member_Application::getApplicationID).collect(Collectors.toList()));
		dto.setNameChangeRequestIds(parseCsvAsIntegers(entity.getNameChangeRequestIdsCsv()));
		dto.setNomineeChangeRequestIds(parseCsvAsIntegers(entity.getNomineeChangeRequestIdsCsv()));
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

	// ── Create ───────────────────────────────────────────────────────────

	public BoardApprovalListDTO createBoardApprovalList(BoardApprovalListDTO dto) {
		boolean hasApplications = dto.getApplicationIds() != null && !dto.getApplicationIds().isEmpty();
		boolean hasNameChangeRequests = dto.getNameChangeRequestIds() != null && !dto.getNameChangeRequestIds().isEmpty();
		boolean hasNomineeChangeRequests = dto.getNomineeChangeRequestIds() != null && !dto.getNomineeChangeRequestIds().isEmpty();

		if (!hasApplications && !hasNameChangeRequests && !hasNomineeChangeRequests) {
			throw new RuntimeException("No applications, name change requests, or nominee change requests selected for board approval list");
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

		// Store application IDs as entity relation and update statuses
		if (hasApplications) {
			for (String applicationId : dto.getApplicationIds()) {
				Member_Application application = memberApplicationRepository.findByApplicationID(applicationId)
						.orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));
				application.setStatus(ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST);
				memberApplicationRepository.save(application);
				entity.getApplications().add(application);
			}
		}

		// Store name change request IDs as CSV and update statuses
		if (hasNameChangeRequests) {
			entity.setNameChangeRequestIdsCsv(serializeIntegerIds(dto.getNameChangeRequestIds()));
			for (Integer id : dto.getNameChangeRequestIds()) {
				NameChangeRequest ncr = nameChangeRequestRepo.findById(id)
						.orElseThrow(() -> new RuntimeException("Name change request not found: " + id));
				ncr.setNewStatus(ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST);
				nameChangeRequestRepo.save(ncr);
			}
		}

		// Store nominee change request IDs as CSV and update statuses
		if (hasNomineeChangeRequests) {
			entity.setNomineeChangeRequestIdsCsv(serializeIntegerIds(dto.getNomineeChangeRequestIds()));
			for (Integer id : dto.getNomineeChangeRequestIds()) {
				NommineChangeRequests ncr = nomineeChangeRequestRepo.findById(id)
						.orElseThrow(() -> new RuntimeException("Nominee change request not found: " + id));
				ncr.setStatus(ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST);
				nomineeChangeRequestRepo.save(ncr);
			}
		}

		BoardApprovalList saved = boardApprovalListRepository.save(entity);
		return toDto(saved);
	}

	// ── Read ─────────────────────────────────────────────────────────────

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

	public List<NameChangeRequestDTO> getNameChangeRequestsByListId(String listId) {
		BoardApprovalList entity = boardApprovalListRepository.findByListId(listId)
				.orElseThrow(() -> new RuntimeException("Board approval list not found"));

		List<Integer> ids = parseCsvAsIntegers(entity.getNameChangeRequestIdsCsv());
		if (ids.isEmpty()) {
			return Collections.emptyList();
		}

		return ids.stream()
				.map(id -> nameChangeRequestRepo.findById(id)
						.map(ncr -> {
							NameChangeRequestDTO dto = new NameChangeRequestDTO();
							dto.setNameChangeRequestID(String.valueOf(ncr.getNameChangeRequestID()));
							dto.setNewTitle(ncr.getNewTitle());
							dto.setNewFullName(ncr.getNewFullName());
							dto.setNewNameAsInPayroll(ncr.getNewNameAsInPayroll());
							dto.setNewNameWithInitials(ncr.getNewNameWithInitials());
							dto.setNewStatus(ncr.getNewStatus());
							return dto;
						})
						.orElseThrow(() -> new RuntimeException("Name change request not found: " + id)))
				.collect(Collectors.toList());
	}

	public List<NommineChangeRequestDTO> getNomineeChangeRequestsByListId(String listId) {
		BoardApprovalList entity = boardApprovalListRepository.findByListId(listId)
				.orElseThrow(() -> new RuntimeException("Board approval list not found"));

		List<Integer> ids = parseCsvAsIntegers(entity.getNomineeChangeRequestIdsCsv());
		if (ids.isEmpty()) {
			return Collections.emptyList();
		}

		return ids.stream()
				.map(id -> nomineeChangeRequestRepo.findById(id)
						.map(ncr -> {
							NommineChangeRequestDTO dto = new NommineChangeRequestDTO();
							dto.setId(ncr.getId());
							dto.setNewnommineName(ncr.getNewnommineName());
							dto.setRelationship(ncr.getRelationship());
							dto.setNic(ncr.getNic());
							dto.setAddress(ncr.getAddress());
							dto.setNewStatus(ncr.getStatus());
							return dto;
						})
						.orElseThrow(() -> new RuntimeException("Nominee change request not found: " + id)))
				.collect(Collectors.toList());
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private void writeBoardDecisionReason(MemberApplicationDTO dto, String boardDecisionReason) {
		try {
			Field field = MemberApplicationDTO.class.getDeclaredField("boardDecisionReason");
			field.setAccessible(true);
			field.set(dto, boardDecisionReason);
		} catch (ReflectiveOperationException error) {
			// Ignore if the field is unavailable; the rest of the payload is still valid.
		}
	}

	// ── Process ──────────────────────────────────────────────────────────

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

		// Update name change request statuses
		List<Integer> nameChangeIds = parseCsvAsIntegers(entity.getNameChangeRequestIdsCsv());
		for (Integer id : nameChangeIds) {
			nameChangeRequestRepo.findById(id).ifPresent(ncr -> {
				ncr.setNewStatus(approved ? ApplicationStatus.APPROVED : ApplicationStatus.REJECTED);
				nameChangeRequestRepo.save(ncr);
			});
		}

		// Update nominee change request statuses
		List<Integer> nomineeChangeIds = parseCsvAsIntegers(entity.getNomineeChangeRequestIdsCsv());
		for (Integer id : nomineeChangeIds) {
			nomineeChangeRequestRepo.findById(id).ifPresent(ncr -> {
				ncr.setStatus(approved ? ApplicationStatus.APPROVED : ApplicationStatus.REJECTED);
				nomineeChangeRequestRepo.save(ncr);
			});
		}

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

	// ── Delete ───────────────────────────────────────────────────────────

	public String deleteBoardApprovalList(String listId) {
		BoardApprovalList entity = boardApprovalListRepository.findByListId(listId)
				.orElseThrow(() -> new RuntimeException("Board approval list not found"));

		for (Member_Application application : entity.getApplications()) {
			if (application.getStatus() == ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST) {
				application.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
				memberApplicationRepository.save(application);
			}
		}

		// Reset name change request statuses
		List<Integer> nameChangeIds = parseCsvAsIntegers(entity.getNameChangeRequestIdsCsv());
		for (Integer id : nameChangeIds) {
			nameChangeRequestRepo.findById(id).ifPresent(ncr -> {
				if (ncr.getNewStatus() == ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST) {
					ncr.setNewStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
					nameChangeRequestRepo.save(ncr);
				}
			});
		}

		// Reset nominee change request statuses
		List<Integer> nomineeChangeIds = parseCsvAsIntegers(entity.getNomineeChangeRequestIdsCsv());
		for (Integer id : nomineeChangeIds) {
			nomineeChangeRequestRepo.findById(id).ifPresent(ncr -> {
				if (ncr.getStatus() == ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST) {
					ncr.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
					nomineeChangeRequestRepo.save(ncr);
				}
			});
		}

		boardApprovalListRepository.delete(entity);
		return "Board approval list deleted successfully";
	}
}

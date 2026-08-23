package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.BoardApprovalListDTO;
import com.memberconnect.backend.dto.ProfileChangeItemDecisionDTO;
import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.dto.NameChangeRequestDTO;
import com.memberconnect.backend.dto.NommineChangeRequestDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.ProfileChangeType;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.function.Function;
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
import java.util.LinkedHashMap;
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

	@Autowired
	private ProfileChangeStatusPolicy statusPolicy;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AuditService auditService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NameChangeRequstServices nameChangeRequstServices;

	@Autowired
	private NommineChangeRequestServices nommineChangeRequestServices;

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
				// MR08 rolls a deleted list back to what each application was BEFORE it
				// joined - Submitted for Approval, or Rejected for a previous rejection.
				// That original is only knowable if it is captured before the overwrite.
				application.setStatusBeforeBoardList(application.getStatus());
				application.setStatus(ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST);
				memberApplicationRepository.save(application);
				// From dev: the Progress tab records the application joining the list.
				auditService.record(AuditService.MODULE_APPLICATION, application.getId(),
						"Added to Board Approval List", null, entity.getListId(), null);
				entity.getApplications().add(application);
			}
		}

		// Store name change request IDs as CSV and update statuses
		if (hasNameChangeRequests) {
			entity.setNameChangeRequestIdsCsv(serializeIntegerIds(dto.getNameChangeRequestIds()));
			for (Integer id : dto.getNameChangeRequestIds()) {
				NameChangeRequest ncr = nameChangeRequestRepo.findById(id)
						.orElseThrow(() -> new RuntimeException("Name change request not found: " + id));
				statusPolicy.assertListable(ncr.getStatus(), ncr.getRequestNo());
				ncr.setStatus(ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST);
				nameChangeRequestRepo.save(ncr);
			}
		}

		// Store nominee change request IDs as CSV and update statuses
		if (hasNomineeChangeRequests) {
			entity.setNomineeChangeRequestIdsCsv(serializeIntegerIds(dto.getNomineeChangeRequestIds()));
			for (Integer id : dto.getNomineeChangeRequestIds()) {
				NommineChangeRequests ncr = nomineeChangeRequestRepo.findById(id)
						.orElseThrow(() -> new RuntimeException("Nominee change request not found: " + id));
				statusPolicy.assertListable(ncr.getStatus(), ncr.getRequestNo());
				ncr.setStatus(ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST);
				nomineeChangeRequestRepo.save(ncr);
			}
		}

		BoardApprovalList saved = boardApprovalListRepository.save(entity);
		return toDto(saved);
	}

	// ── Per-request decisions (MMC12 / MMC25) ────────────────────────────

	/** Indexes the submitted decisions by request id so each row can find its own. */
	private Map<Integer, ProfileChangeItemDecisionDTO> byRequestId(List<ProfileChangeItemDecisionDTO> decisions) {
		if (decisions == null) {
			return Map.of();
		}
		return decisions.stream()
				.filter(d -> d.getRequestId() != null)
				.collect(Collectors.toMap(
						ProfileChangeItemDecisionDTO::getRequestId,
						Function.identity(),
						(first, second) -> second));
	}

	/**
	 * MMC12: on approval the Member Profile takes the requested names; on rejection it
	 * is left untouched and the reason is stored. Both outcomes are audited and notified.
	 *
	 * Approval previously changed only the request's status — the member's name was never
	 * actually updated, so an approved name change had no effect on the member.
	 */
	private void processNameChangeRequests(
			List<Integer> ids,
			List<ProfileChangeItemDecisionDTO> decisions,
			String processedBy) {

		Map<Integer, ProfileChangeItemDecisionDTO> byId = byRequestId(decisions);

		for (Integer id : ids) {
			NameChangeRequest request = nameChangeRequestRepo.findById(id).orElse(null);
			if (request == null) {
				continue;
			}

			ProfileChangeItemDecisionDTO decision = byId.get(id);
			boolean reject = decision != null && decision.isReject();

			if (reject) {
				String reason = decision.getRejectReason() == null ? "" : decision.getRejectReason().trim();
				if (reason.isEmpty()) {
					throw new ResponseStatusException(
							HttpStatus.BAD_REQUEST,
							"A reject reason is required for request "
									+ (request.getRequestNo() == null ? id : request.getRequestNo()) + ".");
				}
				request.setStatus(ApplicationStatus.REJECTED);
				request.setRejectReason(reason);
			} else {
				request.setStatus(ApplicationStatus.APPROVED);
				request.setRejectReason(null);
			}

			request.setProcessedBy(processedBy);
			request.setProcessedAt(LocalDateTime.now());
			NameChangeRequest saved = nameChangeRequestRepo.save(request);

			Member member = saved.getMemberId() == null
					? null
					: memberRepository.findByMemberId(saved.getMemberId()).orElse(null);
			if (member == null) {
				// Nothing to update or notify against; the status change still stands.
				continue;
			}

			if (reject) {
				auditService.record(
						AuditService.MODULE_NAME_CHANGE, member.getId(), "REJECTED", null, null,
						"Request " + saved.getRequestNo() + " rejected: " + saved.getRejectReason());
				notificationService.sendProfileChangeRejected(
						member, ProfileChangeType.NAME, saved.getRequestNo(), saved.getRejectReason());
				continue;
			}

			Map<String, Object> before = nameSnapshot(member);
			applyNamesToMember(saved, member);
			Map<String, Object> after = nameSnapshot(member);
			memberRepository.save(member);

			auditService.recordFieldChanges(
					AuditService.MODULE_NAME_CHANGE, member.getId(), "APPROVED", before, after,
					"Request " + saved.getRequestNo() + " approved");
			notificationService.sendProfileChangeApproved(
					member, ProfileChangeType.NAME, saved.getRequestNo());
		}
	}

	/** MMC25, the same shape as the name path but writing the nominee fields. */
	private void processNomineeChangeRequests(
			List<Integer> ids,
			List<ProfileChangeItemDecisionDTO> decisions,
			String processedBy) {

		Map<Integer, ProfileChangeItemDecisionDTO> byId = byRequestId(decisions);

		for (Integer id : ids) {
			NommineChangeRequests request = nomineeChangeRequestRepo.findById(id).orElse(null);
			if (request == null) {
				continue;
			}

			ProfileChangeItemDecisionDTO decision = byId.get(id);
			boolean reject = decision != null && decision.isReject();

			if (reject) {
				String reason = decision.getRejectReason() == null ? "" : decision.getRejectReason().trim();
				if (reason.isEmpty()) {
					throw new ResponseStatusException(
							HttpStatus.BAD_REQUEST,
							"A reject reason is required for request "
									+ (request.getRequestNo() == null ? id : request.getRequestNo()) + ".");
				}
				request.setStatus(ApplicationStatus.REJECTED);
				request.setRejectReason(reason);
			} else {
				request.setStatus(ApplicationStatus.APPROVED);
				request.setRejectReason(null);
			}

			request.setProcessedBy(processedBy);
			request.setProcessedAt(LocalDateTime.now());
			NommineChangeRequests saved = nomineeChangeRequestRepo.save(request);

			Member member = saved.getMemberId() == null
					? null
					: memberRepository.findByMemberId(saved.getMemberId()).orElse(null);
			if (member == null) {
				continue;
			}

			if (reject) {
				auditService.record(
						AuditService.MODULE_NOMINEE_CHANGE, member.getId(), "REJECTED", null, null,
						"Request " + saved.getRequestNo() + " rejected: " + saved.getRejectReason());
				notificationService.sendProfileChangeRejected(
						member, ProfileChangeType.NOMINEE, saved.getRequestNo(), saved.getRejectReason());
				continue;
			}

			Map<String, Object> before = nomineeSnapshot(member);
			applyNomineeToMember(saved, member);
			Map<String, Object> after = nomineeSnapshot(member);
			memberRepository.save(member);

			auditService.recordFieldChanges(
					AuditService.MODULE_NOMINEE_CHANGE, member.getId(), "APPROVED", before, after,
					"Request " + saved.getRequestNo() + " approved");
			notificationService.sendProfileChangeApproved(
					member, ProfileChangeType.NOMINEE, saved.getRequestNo());
		}
	}

	private void applyNamesToMember(NameChangeRequest request, Member member) {
		setIfPresent(request.getNewTitle(), member::setTitle);
		setIfPresent(request.getNewFullName(), member::setFullName);
		setIfPresent(request.getNewNameAsInPayroll(), member::setNameAsInPayroll);
		setIfPresent(request.getNewNameWithInitials(), member::setNameWithInitials);
	}

	private void applyNomineeToMember(NommineChangeRequests request, Member member) {
		setIfPresent(request.getNewnommineName(), member::setNomineeFullName);
		setIfPresent(request.getRelationship(), member::setNomineeRelationship);
		setIfPresent(request.getAddress(), member::setNomineeAddress);
		// MMC18 lists the nominee's Identification Number among the changeable fields; on
		// the member it is identificationNumber, not nic, which is the member's own.
		setIfPresent(request.getNic(), member::setIdentificationNumber);
	}

	private Map<String, Object> nameSnapshot(Member member) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("title", String.valueOf(member.getTitle()));
		values.put("fullName", String.valueOf(member.getFullName()));
		values.put("nameAsInPayroll", String.valueOf(member.getNameAsInPayroll()));
		values.put("nameWithInitials", String.valueOf(member.getNameWithInitials()));
		return values;
	}

	private Map<String, Object> nomineeSnapshot(Member member) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("nomineeFullName", String.valueOf(member.getNomineeFullName()));
		values.put("nomineeRelationship", String.valueOf(member.getNomineeRelationship()));
		values.put("nomineeAddress", String.valueOf(member.getNomineeAddress()));
		values.put("nomineeIdentificationNumber", String.valueOf(member.getIdentificationNumber()));
		return values;
	}

	/** A blank requested value leaves the member's current value alone. */
	private void setIfPresent(String value, java.util.function.Consumer<String> setter) {
		if (value != null && !value.isBlank()) {
			setter.accept(value.trim());
		}
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

		// Delegates to the type's own service so the DTO arrives complete: request
		// number, member id, the member's name and NIC, and the "Current Value"
		// snapshot alongside the requested one.
		//
		// This method used to hand-build a DTO carrying only the four new names and a
		// status. The board report is unusable on that: MMC11's report has to say whose
		// name is changing and what it currently says, and neither was being returned.
		return ids.stream()
				.map(id -> {
					NameChangeRequestDTO dto = nameChangeRequstServices.getRequestById(id);
					if (dto == null) {
						throw new ResponseStatusException(
								HttpStatus.NOT_FOUND, "Name change request not found: " + id);
					}
					return dto;
				})
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
						.map(ncr -> nommineChangeRequestServices.getNommineChangeRequestById(ncr.getId()))
						.orElseThrow(() -> new ResponseStatusException(
								HttpStatus.NOT_FOUND, "Nominee change request not found: " + id)))
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

		List<Integer> nameChangeIds = parseCsvAsIntegers(entity.getNameChangeRequestIdsCsv());
		List<Integer> nomineeChangeIds = parseCsvAsIntegers(entity.getNomineeChangeRequestIdsCsv());
		boolean hasApplications = !entity.getApplications().isEmpty();

		// A list holding only Name or Nominee change requests carries no list-wide
		// decision: MMC12/MMC25 decide those per request. The screen used to derive the
		// list decision from the application decisions alone, so a change-request-only
		// list produced decision "Reject" with no reason and processing always failed
		// with "Reject reason is required when rejecting applications".
		String decision = dto.getDecision() == null ? "" : dto.getDecision().trim();
		if (decision.isEmpty() && hasApplications) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision is required");
		}

		boolean approved = decision.isEmpty() || "APPROVE".equalsIgnoreCase(decision);
		boolean rejected = "REJECT".equalsIgnoreCase(decision);
		if (!decision.isEmpty() && !approved && !rejected) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be Approve or Reject");
		}

		if (rejected && hasApplications
				&& (dto.getRejectReason() == null || dto.getRejectReason().trim().isEmpty())) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "Reject reason is required when rejecting applications");
		}

		LocalDate actualMeetingDate = dto.getActualMeetingDate() != null ? dto.getActualMeetingDate() : LocalDate.now();
		String boardRemarks = dto.getBoardRemarks();
		// Null-safe: a change-request-only list is not required to carry a list-level
		// reject reason, because its rejections are recorded per request instead.
		String rejectReason = rejected && dto.getRejectReason() != null
				? dto.getRejectReason().trim()
				: null;

		// Note: We deliberately DO NOT update individual Member_Application statuses here.
		// The frontend handles per-application Approve/Reject updates via `updateMemberApplicationPartial`.

		String processedBy = dto.getProcessedBy() == null || dto.getProcessedBy().trim().isEmpty()
				? statusPolicy.currentUsername()
				: dto.getProcessedBy().trim();

		processNameChangeRequests(nameChangeIds, dto.getNameChangeDecisions(), processedBy);
		processNomineeChangeRequests(nomineeChangeIds, dto.getNomineeChangeDecisions(), processedBy);

		entity.setStatus("PROCESSED");
		entity.setProcessedAt(LocalDateTime.now());
		entity.setProcessedBy(processedBy);
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
				// Spec MR08: "rolled back to the Submitted for Approval status or Rejected
				// status depend on what status it was originally". Previously this always
				// wrote Submitted for Approval, which silently erased a prior rejection -
				// a rejected application re-listed and then un-listed came back looking as
				// though it had never been rejected.
				//
				// Rows listed before StatusBeforeBoardList existed have nothing recorded,
				// so they keep the old fallback.
				ApplicationStatus restored = application.getStatusBeforeBoardList() == null
						? ApplicationStatus.SUBMITTED_FOR_APPROVAL
						: application.getStatusBeforeBoardList();
				application.setStatus(restored);
				application.setStatusBeforeBoardList(null);
				memberApplicationRepository.save(application);
			}
		}

		// Reset name change request statuses
		List<Integer> nameChangeIds = parseCsvAsIntegers(entity.getNameChangeRequestIdsCsv());
		for (Integer id : nameChangeIds) {
			nameChangeRequestRepo.findById(id).ifPresent(ncr -> {
				if (ncr.getStatus() == ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST) {
					ncr.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
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

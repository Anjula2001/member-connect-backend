package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.TerminationApprovalListDTO;
import com.memberconnect.backend.dto.TerminationRequestDecisionDTO;
import com.memberconnect.backend.dto.TerminationRequestResponseDTO;
import com.memberconnect.backend.enums.TerminationApprovalListStatus;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.model.TerminationApprovalList;
import com.memberconnect.backend.model.TerminationRequest;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.TerminationApprovalListRepository;
import com.memberconnect.backend.repository.TerminationRequestRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@SuppressWarnings("null")
public class TerminationApprovalListService {

    private final TerminationApprovalListRepository approvalListRepository;
    private final BoardmeetingRepository boardMeetingRepository;
    private final TerminationRequestRepository terminationRequestRepository;
    private final TerminationService terminationService;
    private final AuditService auditService;

    public TerminationApprovalListService(
            TerminationApprovalListRepository approvalListRepository,
            BoardmeetingRepository boardMeetingRepository,
            TerminationRequestRepository terminationRequestRepository,
            TerminationService terminationService,
            AuditService auditService
    ) {
        this.approvalListRepository = approvalListRepository;
        this.boardMeetingRepository = boardMeetingRepository;
        this.terminationRequestRepository = terminationRequestRepository;
        this.terminationService = terminationService;
        this.auditService = auditService;
    }

    public TerminationApprovalListDTO createApprovalList(TerminationApprovalListDTO dto) {
        if (dto.getRequestNos() == null || dto.getRequestNos().isEmpty()) {
            throw new RuntimeException("No termination requests selected for approval list");
        }

        if (dto.getBoardMeetingId() == null) {
            throw new RuntimeException("Board meeting is required");
        }

        BoardMeeting boardMeeting = boardMeetingRepository.findById(dto.getBoardMeetingId())
                .orElseThrow(() -> new RuntimeException("Board Meeting not found"));

        TerminationApprovalList entity = new TerminationApprovalList();
        entity.setListId("TAL-" + System.currentTimeMillis());
        entity.setBoardMeetingId(boardMeeting.getId());
        entity.setBoardMeetingDate(boardMeeting.getScheduledDate());
        entity.setStatus(TerminationApprovalListStatus.CREATED);
        entity.setCreatedAt(LocalDateTime.now());

        for (String requestNo : dto.getRequestNos()) {
            TerminationRequest request = terminationRequestRepository.findByRequestNo(requestNo)
                    .orElseThrow(() -> new RuntimeException("Termination request not found: " + requestNo));

            if (request.getStatus() != TerminationRequestStatus.SUBMITTED_FOR_APPROVAL
                    && request.getStatus() != TerminationRequestStatus.REJECTED) {
                throw new RuntimeException(
                        "Only Submitted for Approval or Rejected requests can be added to an approval list: "
                                + requestNo
                );
            }

            // MMT07: remembered now so deleting the list can put the request
            // back where it was, rather than assuming Submitted for Approval.
            TerminationRequestStatus previousStatus = request.getStatus();
            request.setPreviousStatus(previousStatus);
            request.setStatus(TerminationRequestStatus.ADDED_TO_APPROVAL_LIST);
            terminationRequestRepository.save(request);
            entity.getRequests().add(request);

            auditService.recordStatusChange(
                    AuditService.MODULE_TERMINATION,
                    request.getId(),
                    "ADDED_TO_APPROVAL_LIST",
                    previousStatus,
                    request.getStatus(),
                    "Added to termination approval list " + entity.getListId()
            );
        }

        TerminationApprovalList saved = approvalListRepository.save(entity);

        auditService.record(
                AuditService.MODULE_TERMINATION_APPROVAL_LIST,
                saved.getId(),
                "CREATE_LIST",
                null,
                "listId=" + saved.getListId()
                        + ", boardMeetingId=" + saved.getBoardMeetingId()
                        + ", requests=" + saved.getRequests().size(),
                "Termination approval list assembled for the board meeting on "
                        + saved.getBoardMeetingDate()
        );

        return toDto(saved);
    }

    public List<TerminationApprovalListDTO> getAllApprovalLists() {
        return approvalListRepository.findAllWithRequests().stream()
                .map(this::toDto)
                .toList();
    }

    public TerminationApprovalListDTO getApprovalListByListId(String listId) {
        TerminationApprovalList entity = approvalListRepository.findByListIdWithRequests(listId)
                .orElseThrow(() -> new RuntimeException("Termination approval list not found"));
        return toDto(entity);
    }

    public List<TerminationRequestResponseDTO> getRequestsByListId(String listId) {
        TerminationApprovalList entity = approvalListRepository.findByListIdWithRequests(listId)
                .orElseThrow(() -> new RuntimeException("Termination approval list not found"));

        if (entity.getRequests().isEmpty()) {
            return Collections.emptyList();
        }

        return entity.getRequests().stream()
                .map(terminationService::mapRequestToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Records the board's outcome for a Termination Approval List (SRS MMT09).
     *
     * The board decides each request in the list separately, so the caller sends
     * one decision per request and this method applies all of them inside the
     * single surrounding transaction. That matters for two reasons:
     *
     * 1. Validation is complete before anything is written. If one rejection is
     *    missing its mandatory reason, nothing at all is applied - the meeting is
     *    re-recorded from a clean state rather than half-processed.
     * 2. A member's status and their request's status can never drift apart. The
     *    previous implementation left per-request updates to the browser, which
     *    meant a failed call silently produced a list marked processed whose
     *    members were still sitting in TERMINATION_REQUESTED.
     *
     * The list-level decision is derived here, never taken from the client: a
     * list holding both outcomes is recorded as "Mixed" rather than collapsing
     * to whichever verdict happened to appear first.
     */
    public TerminationApprovalListDTO processApprovalList(String listId, TerminationApprovalListDTO dto) {
        TerminationApprovalList entity = approvalListRepository.findByListIdWithRequests(listId)
                .orElseThrow(() -> new RuntimeException("Termination approval list not found"));

        // A board meeting is recorded once. Re-processing would drive already
        // approved requests back through the status guards and fail confusingly,
        // so it is refused up front with a message that says why.
        if (entity.getStatus() == TerminationApprovalListStatus.PROCESSED) {
            throw new RuntimeException(
                    "Termination approval list " + listId + " has already been processed on "
                            + entity.getProcessedAt() + " by " + entity.getProcessedBy()
            );
        }

        if (entity.getRequests().isEmpty()) {
            throw new RuntimeException("Termination approval list " + listId + " has no requests to process");
        }

        Map<String, TerminationRequestDecisionDTO> decisions = indexDecisions(dto.getRequestDecisions());
        validateDecisionsCoverList(entity, decisions);

        int approvedCount = 0;
        int rejectedCount = 0;

        // Iterate the list, not the payload: the list is the authority on what the
        // board actually considered, and validateDecisionsCoverList has already
        // proved the two agree.
        for (TerminationRequest request : entity.getRequests()) {
            TerminationRequestDecisionDTO decision = decisions.get(request.getRequestNo());

            if (isApprove(decision.getDecision())) {
                terminationService.approveRequest(request.getRequestNo());
                approvedCount++;
            } else {
                terminationService.rejectRequest(
                        request.getRequestNo(),
                        decision.getRejectReason().trim()
                );
                rejectedCount++;
            }
        }

        entity.setStatus(TerminationApprovalListStatus.PROCESSED);
        entity.setProcessedAt(LocalDateTime.now());
        entity.setProcessedBy(resolveProcessedBy(dto.getProcessedBy()));
        entity.setActualMeetingDate(
                dto.getActualMeetingDate() != null ? dto.getActualMeetingDate() : entity.getBoardMeetingDate()
        );
        entity.setDecision(deriveListDecision(approvedCount, rejectedCount));
        entity.setRejectReason(null);
        entity.setBoardRemarks(dto.getBoardRemarks());

        // The scanned, board-signed report (MMT09). Only overwritten when one is
        // supplied, so re-reading the list never clears an attachment.
        if (trimToNull(dto.getApprovedListDocument()) != null) {
            entity.setApprovedListDocument(dto.getApprovedListDocument().trim());
        }

        TerminationApprovalList saved = approvalListRepository.save(entity);

        auditService.record(
                AuditService.MODULE_TERMINATION_APPROVAL_LIST,
                saved.getId(),
                "PROCESS_LIST",
                "status=" + TerminationApprovalListStatus.CREATED,
                "status=" + saved.getStatus() + ", decision=" + saved.getDecision()
                        + ", approved=" + approvedCount + ", rejected=" + rejectedCount,
                "Board decisions recorded for " + saved.getListId() + " by " + saved.getProcessedBy()
                        + (saved.getBoardRemarks() != null ? "; remarks: " + saved.getBoardRemarks() : "")
        );

        TerminationApprovalListDTO result = toDto(saved);
        result.setApprovedCount(approvedCount);
        result.setRejectedCount(rejectedCount);
        result.setRequestDecisions(new ArrayList<>(decisions.values()));
        return result;
    }

    public String deleteApprovalList(String listId) {
        TerminationApprovalList entity = approvalListRepository.findByListIdWithRequests(listId)
                .orElseThrow(() -> new RuntimeException("Termination approval list not found"));

        for (TerminationRequest request : entity.getRequests()) {
            if (request.getStatus() == TerminationRequestStatus.ADDED_TO_APPROVAL_LIST) {
                // Rows listed before previous_status existed have nothing
                // recorded; Submitted for Approval is the only safe assumption
                // for those, and is what the old code always did.
                TerminationRequestStatus restored = request.getPreviousStatus() != null
                        ? request.getPreviousStatus()
                        : TerminationRequestStatus.SUBMITTED_FOR_APPROVAL;

                request.setStatus(restored);
                request.setPreviousStatus(null);
                terminationRequestRepository.save(request);

                auditService.recordStatusChange(
                        AuditService.MODULE_TERMINATION,
                        request.getId(),
                        "REMOVED_FROM_APPROVAL_LIST",
                        TerminationRequestStatus.ADDED_TO_APPROVAL_LIST,
                        restored,
                        "Approval list " + entity.getListId() + " deleted; request rolled back"
                );
            }
        }

        // Written before the delete: once the row is gone its id is no longer
        // readable, and an audit trail that loses the deletion is the one gap
        // that matters most on a list the board was about to sit on.
        auditService.record(
                AuditService.MODULE_TERMINATION_APPROVAL_LIST,
                entity.getId(),
                "DELETE_LIST",
                "listId=" + entity.getListId()
                        + ", status=" + entity.getStatus()
                        + ", requests=" + entity.getRequests().size(),
                null,
                "Termination approval list deleted; "
                        + entity.getRequests().size() + " request(s) returned to their prior status"
        );

        approvalListRepository.delete(entity);
        return "Termination approval list deleted successfully";
    }

    /**
     * Keys the incoming decisions by request number, rejecting duplicates rather
     * than letting a later entry silently overwrite an earlier one - two
     * conflicting verdicts for the same member is a caller bug, not a preference.
     */
    private Map<String, TerminationRequestDecisionDTO> indexDecisions(
            List<TerminationRequestDecisionDTO> requestDecisions
    ) {
        if (requestDecisions == null || requestDecisions.isEmpty()) {
            throw new RuntimeException("A board decision is required for every request in the list");
        }

        Map<String, TerminationRequestDecisionDTO> indexed = new HashMap<>();

        for (TerminationRequestDecisionDTO decision : requestDecisions) {
            String requestNo = trimToNull(decision.getRequestNo());

            if (requestNo == null) {
                throw new RuntimeException("A board decision was supplied without a request number");
            }

            if (!isApprove(decision.getDecision()) && !isReject(decision.getDecision())) {
                throw new RuntimeException(
                        "Decision for " + requestNo + " must be Approve or Reject"
                );
            }

            // SRS MMT09: the reason is mandatory for every rejected request before
            // the user may proceed with the approval process.
            if (isReject(decision.getDecision()) && trimToNull(decision.getRejectReason()) == null) {
                throw new RuntimeException(
                        "A rejection reason is required for " + requestNo
                );
            }

            if (indexed.putIfAbsent(requestNo, decision) != null) {
                throw new RuntimeException("Duplicate board decision supplied for " + requestNo);
            }
        }

        return indexed;
    }

    /**
     * Every request in the list must have a decision, and every decision must
     * belong to the list. Both directions are checked so a stale browser tab
     * cannot approve a request the board never saw.
     */
    private void validateDecisionsCoverList(
            TerminationApprovalList entity,
            Map<String, TerminationRequestDecisionDTO> decisions
    ) {
        Set<String> listRequestNos = entity.getRequests().stream()
                .map(TerminationRequest::getRequestNo)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> missing = listRequestNos.stream()
                .filter(requestNo -> !decisions.containsKey(requestNo))
                .toList();

        if (!missing.isEmpty()) {
            throw new RuntimeException(
                    "No board decision supplied for: " + String.join(", ", missing)
            );
        }

        List<String> unknown = decisions.keySet().stream()
                .filter(requestNo -> !listRequestNos.contains(requestNo))
                .sorted()
                .toList();

        if (!unknown.isEmpty()) {
            throw new RuntimeException(
                    "These requests are not part of approval list " + entity.getListId()
                            + ": " + String.join(", ", unknown)
            );
        }
    }

    private String deriveListDecision(int approvedCount, int rejectedCount) {
        if (approvedCount > 0 && rejectedCount > 0) {
            return "Mixed";
        }
        return rejectedCount > 0 ? "Reject" : "Approve";
    }

    /**
     * Prefers the authenticated user over anything the client supplied - who
     * processed a board list is an audit fact, not a form field. The principal is
     * the User entity itself (see JwtFilter), so no extra lookup is needed. Falls
     * back to the supplied value only when there is no authentication at all,
     * which keeps direct service-level tests workable.
     */
    private String resolveProcessedBy(String suppliedProcessedBy) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getFullName() != null && !user.getFullName().isBlank()
                    ? user.getFullName()
                    : user.getUsername();
        }

        String supplied = trimToNull(suppliedProcessedBy);
        return supplied != null ? supplied : "Head Office User";
    }

    private boolean isApprove(String decision) {
        return decision != null && "APPROVE".equalsIgnoreCase(decision.trim());
    }

    private boolean isReject(String decision) {
        return decision != null && "REJECT".equalsIgnoreCase(decision.trim());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TerminationApprovalListDTO toDto(TerminationApprovalList entity) {
        TerminationApprovalListDTO dto = new TerminationApprovalListDTO();
        dto.setId(entity.getId());
        dto.setListId(entity.getListId());
        dto.setBoardMeetingId(entity.getBoardMeetingId());
        dto.setBoardMeetingDate(entity.getBoardMeetingDate());
        dto.setRequestNos(
                entity.getRequests().stream()
                        .map(TerminationRequest::getRequestNo)
                        .collect(Collectors.toList())
        );
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
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
}

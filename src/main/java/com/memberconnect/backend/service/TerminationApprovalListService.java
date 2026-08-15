package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.TerminationApprovalListDTO;
import com.memberconnect.backend.dto.TerminationRequestResponseDTO;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.model.TerminationApprovalList;
import com.memberconnect.backend.model.TerminationRequest;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.TerminationApprovalListRepository;
import com.memberconnect.backend.repository.TerminationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@SuppressWarnings("null")
public class TerminationApprovalListService {

    private final TerminationApprovalListRepository approvalListRepository;
    private final BoardmeetingRepository boardMeetingRepository;
    private final TerminationRequestRepository terminationRequestRepository;
    private final TerminationService terminationService;

    public TerminationApprovalListService(
            TerminationApprovalListRepository approvalListRepository,
            BoardmeetingRepository boardMeetingRepository,
            TerminationRequestRepository terminationRequestRepository,
            TerminationService terminationService
    ) {
        this.approvalListRepository = approvalListRepository;
        this.boardMeetingRepository = boardMeetingRepository;
        this.terminationRequestRepository = terminationRequestRepository;
        this.terminationService = terminationService;
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
        entity.setStatus("CREATED");
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

            request.setStatus(TerminationRequestStatus.ADDED_TO_APPROVAL_LIST);
            terminationRequestRepository.save(request);
            entity.getRequests().add(request);
        }

        TerminationApprovalList saved = approvalListRepository.save(entity);
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

    public TerminationApprovalListDTO processApprovalList(String listId, TerminationApprovalListDTO dto) {
        TerminationApprovalList entity = approvalListRepository.findByListId(listId)
                .orElseThrow(() -> new RuntimeException("Termination approval list not found"));

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
            throw new RuntimeException("Reject reason is required when rejecting requests");
        }

        entity.setStatus("PROCESSED");
        entity.setProcessedAt(LocalDateTime.now());
        entity.setProcessedBy(
                dto.getProcessedBy() == null || dto.getProcessedBy().trim().isEmpty()
                        ? "Head Office User"
                        : dto.getProcessedBy().trim()
        );
        entity.setActualMeetingDate(
                dto.getActualMeetingDate() != null ? dto.getActualMeetingDate() : LocalDate.now()
        );
        entity.setDecision(approved ? "Approve" : "Reject");
        entity.setRejectReason(rejected ? dto.getRejectReason().trim() : null);
        entity.setBoardRemarks(dto.getBoardRemarks());

        TerminationApprovalList saved = approvalListRepository.save(entity);
        return toDto(saved);
    }

    public String deleteApprovalList(String listId) {
        TerminationApprovalList entity = approvalListRepository.findByListId(listId)
                .orElseThrow(() -> new RuntimeException("Termination approval list not found"));

        for (TerminationRequest request : entity.getRequests()) {
            if (request.getStatus() == TerminationRequestStatus.ADDED_TO_APPROVAL_LIST) {
                request.setStatus(TerminationRequestStatus.SUBMITTED_FOR_APPROVAL);
                terminationRequestRepository.save(request);
            }
        }

        approvalListRepository.delete(entity);
        return "Termination approval list deleted successfully";
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
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setProcessedAt(entity.getProcessedAt());
        dto.setProcessedBy(entity.getProcessedBy());
        dto.setActualMeetingDate(entity.getActualMeetingDate());
        dto.setDecision(entity.getDecision());
        dto.setRejectReason(entity.getRejectReason());
        dto.setBoardRemarks(entity.getBoardRemarks());
        return dto;
    }
}

package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memberconnect.backend.dto.TerminationApprovalListDTO;
import com.memberconnect.backend.dto.TerminationRequestResponseDTO;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.model.TerminationApprovalList;
import com.memberconnect.backend.model.TerminationApprovalListItem;
import com.memberconnect.backend.model.TerminationRequest;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.TerminationApprovalListRepository;
import com.memberconnect.backend.repository.TerminationRequestRepository;

@Service
@Transactional
@SuppressWarnings("null")
public class TerminationApprovalListService {

    private final TerminationApprovalListRepository listRepository;
    private final BoardmeetingRepository boardMeetingRepository;
    private final TerminationRequestRepository requestRepository;
    private final TerminationService terminationService;

    public TerminationApprovalListService(
            TerminationApprovalListRepository listRepository,
            BoardmeetingRepository boardMeetingRepository,
            TerminationRequestRepository requestRepository,
            TerminationService terminationService
    ) {
        this.listRepository = listRepository;
        this.boardMeetingRepository = boardMeetingRepository;
        this.requestRepository = requestRepository;
        this.terminationService = terminationService;
    }

    public TerminationApprovalListDTO createTerminationApprovalList(TerminationApprovalListDTO dto) {
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
            TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                    .orElseThrow(() -> new RuntimeException("Termination request not found: " + requestNo));

            if (request.getStatus() != TerminationRequestStatus.SUBMITTED_FOR_APPROVAL
                    && request.getStatus() != TerminationRequestStatus.REJECTED) {
                throw new RuntimeException(
                        "Only Submitted for Approval or Rejected requests can be added to an approval list: "
                                + requestNo
                );
            }

            TerminationApprovalListItem item = new TerminationApprovalListItem();
            item.setList(entity);
            item.setRequest(request);
            item.setPreviousStatus(request.getStatus());
            entity.getItems().add(item);

            request.setStatus(TerminationRequestStatus.ADDED_TO_APPROVAL_LIST);
            requestRepository.save(request);
        }

        TerminationApprovalList saved = listRepository.save(entity);
        return toDto(saved);
    }

    public List<TerminationApprovalListDTO> getAllTerminationApprovalLists() {
        return listRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public TerminationApprovalListDTO getTerminationApprovalListByListId(String listId) {
        TerminationApprovalList entity = listRepository.findByListId(listId)
                .orElseThrow(() -> new RuntimeException("Termination approval list not found"));
        return toDto(entity);
    }

    public List<TerminationRequestResponseDTO> getRequestsByListId(String listId) {
        TerminationApprovalList entity = listRepository.findByListId(listId)
                .orElseThrow(() -> new RuntimeException("Termination approval list not found"));

        if (entity.getItems().isEmpty()) {
            return Collections.emptyList();
        }

        return entity.getItems().stream()
                .map(item -> terminationService.mapRequestToResponse(item.getRequest()))
                .collect(Collectors.toList());
    }

    public TerminationApprovalListDTO processTerminationApprovalList(
            String listId,
            TerminationApprovalListDTO dto
    ) {
        TerminationApprovalList entity = listRepository.findByListId(listId)
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
            throw new RuntimeException("Reject reason is required when rejecting the list");
        }

        LocalDate actualMeetingDate = dto.getActualMeetingDate() != null
                ? dto.getActualMeetingDate()
                : LocalDate.now();

        entity.setStatus("PROCESSED");
        entity.setProcessedAt(LocalDateTime.now());
        entity.setProcessedBy(dto.getProcessedBy() == null || dto.getProcessedBy().trim().isEmpty()
                ? "Head Office User"
                : dto.getProcessedBy().trim());
        entity.setActualMeetingDate(actualMeetingDate);
        entity.setDecision(approved ? "Approve" : "Reject");
        entity.setRejectReason(rejected ? dto.getRejectReason().trim() : null);
        entity.setBoardRemarks(dto.getBoardRemarks());

        TerminationApprovalList saved = listRepository.save(entity);
        return toDto(saved);
    }

    public String deleteTerminationApprovalList(String listId) {
        TerminationApprovalList entity = listRepository.findByListId(listId)
                .orElseThrow(() -> new RuntimeException("Termination approval list not found"));

        for (TerminationApprovalListItem item : entity.getItems()) {
            TerminationRequest request = item.getRequest();
            if (request.getStatus() == TerminationRequestStatus.ADDED_TO_APPROVAL_LIST) {
                request.setStatus(item.getPreviousStatus());
                requestRepository.save(request);
            }
        }

        listRepository.delete(entity);
        return "Termination approval list deleted successfully";
    }

    private TerminationApprovalListDTO toDto(TerminationApprovalList entity) {
        TerminationApprovalListDTO dto = new TerminationApprovalListDTO();
        dto.setId(entity.getId());
        dto.setListId(entity.getListId());
        dto.setBoardMeetingId(entity.getBoardMeetingId());
        dto.setBoardMeetingDate(entity.getBoardMeetingDate());
        dto.setRequestNos(entity.getItems().stream()
                .map(item -> item.getRequest().getRequestNo())
                .collect(Collectors.toList()));
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

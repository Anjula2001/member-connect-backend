package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.TerminationApprovalListDTO;
import com.memberconnect.backend.enums.TerminationStatus;
import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.model.MemberTermination;
import com.memberconnect.backend.model.TerminationApprovalList;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.MemberTerminationRepository;
import com.memberconnect.backend.repository.TerminationApprovalListRepository;
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
public class TerminationApprovalListService {

    @Autowired
    private TerminationApprovalListRepository terminationApprovalListRepository;

    @Autowired
    private BoardmeetingRepository boardMeetingRepository;

    @Autowired
    private MemberTerminationRepository memberTerminationRepository;


    private List<String> parseCsv(String csv) {
        if (csv == null || csv.trim().isEmpty())
            return Collections.emptyList();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toList());
    }

    private String toCsv(List<String> ids) {
        if (ids == null || ids.isEmpty())
            return "";
        return String.join(",", ids);
    }

    private TerminationApprovalListDTO toDto(TerminationApprovalList entity) {
        TerminationApprovalListDTO dto = new TerminationApprovalListDTO();
        dto.setId(entity.getId());
        dto.setListId(entity.getListId());
        dto.setBoardMeetingId(entity.getBoardMeetingId());
        dto.setBoardMeetingDate(entity.getBoardMeetingDate() != null ? entity.getBoardMeetingDate().toString() : null);
        dto.setTerminationIds(parseCsv(entity.getTerminationIdsCsv()));
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        dto.setProcessedAt(entity.getProcessedAt() != null ? entity.getProcessedAt().toString() : null);
        dto.setProcessedBy(entity.getProcessedBy());
        dto.setActualMeetingDate(
                entity.getActualMeetingDate() != null ? entity.getActualMeetingDate().toString() : null);
        dto.setDecision(entity.getDecision());
        dto.setRejectReason(entity.getRejectReason());
        dto.setBoardRemarks(entity.getBoardRemarks());
        return dto;
    }

   

    public TerminationApprovalListDTO createTerminationApprovalList(TerminationApprovalListDTO dto) {
        if (dto.getTerminationIds() == null || dto.getTerminationIds().isEmpty()) {
            throw new RuntimeException("No termination requests selected");
        }
        if (dto.getBoardMeetingId() == null) {
            throw new RuntimeException("Board meeting is required");
        }

        BoardMeeting boardMeeting = boardMeetingRepository.findById(dto.getBoardMeetingId())
                .orElseThrow(() -> new RuntimeException("Board Meeting not found with id: " + dto.getBoardMeetingId()));

        
        List<MemberTermination> terminations = dto.getTerminationIds().stream()
                .map(rawId -> {
                    try {
                        Long numericId = Long.parseLong(rawId);
                        return memberTerminationRepository.findById(numericId)
                                .orElseThrow(() -> new RuntimeException("Termination record not found: " + rawId));
                    } catch (NumberFormatException e) {
                        return memberTerminationRepository.findByTerminationId(rawId)
                                .orElseThrow(() -> new RuntimeException("Termination record not found: " + rawId));
                    }
                })
                .collect(Collectors.toList());

        // Build the approval list entity
        TerminationApprovalList entity = new TerminationApprovalList();
        entity.setListId("TAL-" + System.currentTimeMillis());
        entity.setBoardMeetingId(boardMeeting.getId());
        entity.setBoardMeetingDate(boardMeeting.getScheduledDate());
        entity.setTerminationIdsCsv(toCsv(dto.getTerminationIds()));
        entity.setStatus("CREATED");
        entity.setCreatedAt(LocalDateTime.now());

        TerminationApprovalList saved = terminationApprovalListRepository.save(entity);

        // Update each termination's status to ADDED_TO_APPROVAL_LIST
        for (MemberTermination term : terminations) {
            term.setTerminationStatus(TerminationStatus.ADDED_TO_APPROVAL_LIST);
            memberTerminationRepository.save(term);
        }

        return toDto(saved);
    }

    // Read

    public List<TerminationApprovalListDTO> getAllTerminationApprovalLists() {
        return terminationApprovalListRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public TerminationApprovalListDTO getTerminationApprovalListByListId(String listId) {
        TerminationApprovalList entity = terminationApprovalListRepository.findByListId(listId)
                .orElseThrow(() -> new RuntimeException("Termination approval list not found: " + listId));
        return toDto(entity);
    }

    // Delete 

    public String deleteTerminationApprovalList(String listId) {
        TerminationApprovalList entity = terminationApprovalListRepository.findByListId(listId)
                .orElseThrow(() -> new RuntimeException("Termination approval list not found: " + listId));

        // Revert statuses back to SUBMITTED_FOR_APPROVAL
        for (String rawId : parseCsv(entity.getTerminationIdsCsv())) {
            try {
                Long numericId = Long.parseLong(rawId);
                memberTerminationRepository.findById(numericId).ifPresent(t -> {
                    if (t.getTerminationStatus() == TerminationStatus.ADDED_TO_APPROVAL_LIST) {
                        t.setTerminationStatus(TerminationStatus.SUBMITTED_FOR_APPROVAL);
                        memberTerminationRepository.save(t);
                    }
                });
            } catch (NumberFormatException e) {
                memberTerminationRepository.findByTerminationId(rawId).ifPresent(t -> {
                    if (t.getTerminationStatus() == TerminationStatus.ADDED_TO_APPROVAL_LIST) {
                        t.setTerminationStatus(TerminationStatus.SUBMITTED_FOR_APPROVAL);
                        memberTerminationRepository.save(t);
                    }
                });
            }
        }

        terminationApprovalListRepository.delete(entity);
        return "Termination approval list deleted successfully";
    }
}

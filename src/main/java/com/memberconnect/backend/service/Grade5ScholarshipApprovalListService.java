package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.Grade5ScholarshipApprovalListDTO;
import com.memberconnect.backend.dto.Grade5RequestApprovalDetailDTO;
import com.memberconnect.backend.model.Grade5ScholarshipApprovalList;
import com.memberconnect.backend.model.Grade5ScholarshipRequest;
import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.repository.Grade5ScholarshipApprovalListRepository;
import com.memberconnect.backend.repository.Grade5ScholarshipRepository;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class Grade5ScholarshipApprovalListService {

    @Autowired
    private Grade5ScholarshipApprovalListRepository approvalListRepository;

    @Autowired
    private Grade5ScholarshipRepository scholarshipRepository;

    @Autowired
    private BoardmeetingRepository boardMeetingRepository;

    private Grade5ScholarshipApprovalListDTO toDto(Grade5ScholarshipApprovalList entity) {
        Grade5ScholarshipApprovalListDTO dto = new Grade5ScholarshipApprovalListDTO();
        dto.setId(entity.getId());
        dto.setListId(entity.getListId());
        dto.setBoardMeetingId(entity.getBoardMeetingId());
        dto.setBoardMeetingDate(entity.getBoardMeetingDate());
        dto.setActualMeetingDate(entity.getActualMeetingDate());
        dto.setStatus(entity.getStatus());
        dto.setType(entity.getType());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setProcessedAt(entity.getProcessedAt());
        dto.setProcessedBy(entity.getProcessedBy());
        dto.setScannedReportPath(entity.getScannedReportPath());
        dto.setDecision(entity.getDecision());
        dto.setBoardRemarks(entity.getBoardRemarks());

        // Find attached request numbers
        List<Grade5ScholarshipRequest> requests = scholarshipRepository.findAll().stream()
                .filter(r -> entity.getListId().equals(r.getApprovalListId()))
                .collect(Collectors.toList());
        dto.setRequestNos(requests.stream().map(Grade5ScholarshipRequest::getRequestNo).collect(Collectors.toList()));

        return dto;
    }

    public Grade5ScholarshipApprovalListDTO createApprovalList(Grade5ScholarshipApprovalListDTO dto) {
        if (dto.getRequestNos() == null || dto.getRequestNos().isEmpty()) {
            throw new RuntimeException("No requests selected for approval list");
        }

        if (dto.getBoardMeetingId() == null) {
            throw new RuntimeException("Board meeting is required");
        }

        BoardMeeting boardMeeting = boardMeetingRepository.findById(dto.getBoardMeetingId())
                .orElseThrow(() -> new RuntimeException("Board Meeting not found"));

        Grade5ScholarshipApprovalList entity = new Grade5ScholarshipApprovalList();
        String listType = dto.getType() != null ? dto.getType().toUpperCase() : "NORMAL";
        String prefix = listType.equals("DEVIATION") ? "G5-DAL-" : "G5-NAL-";
        
        List<Grade5ScholarshipApprovalList> existing = approvalListRepository.findByType(listType);
        long maxNum = 0;
        for (Grade5ScholarshipApprovalList list : existing) {
            String listId = list.getListId();
            if (listId != null && listId.startsWith(prefix)) {
                String suffix = listId.substring(prefix.length());
                try {
                    long num = Long.parseLong(suffix);
                    if (num < 1000000) {
                        if (num > maxNum) {
                            maxNum = num;
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignore non-numeric suffixes
                }
            }
        }
        String nextNum = String.format("%03d", maxNum + 1);
        entity.setListId(prefix + nextNum);
        entity.setBoardMeetingId(boardMeeting.getId());
        entity.setBoardMeetingDate(boardMeeting.getScheduledDate());
        entity.setStatus("CREATED");
        entity.setType(listType);
        entity.setCreatedAt(LocalDateTime.now());

        String targetRequestStatus = listType.equals("DEVIATION") 
                ? "ADDED_TO_SCHOLARSHIP_DEVIATION_APPROVAL_LIST" 
                : "ADDED_TO_SCHOLARSHIP_NORMAL_APPROVAL_LIST";

        for (String requestNo : dto.getRequestNos()) {
            Grade5ScholarshipRequest request = scholarshipRepository.findByRequestNo(requestNo)
                    .orElseThrow(() -> new RuntimeException("Grade 5 Scholarship Request not found: " + requestNo));
            
            request.setOriginalStatus(request.getStatus());
            request.setApprovalListId(entity.getListId());
            request.setStatus(targetRequestStatus);
            scholarshipRepository.save(request);
        }

        Grade5ScholarshipApprovalList saved = approvalListRepository.save(entity);
        return toDto(saved);
    }

    public List<Grade5ScholarshipApprovalListDTO> getAllApprovalLists() {
        return approvalListRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Grade5ScholarshipApprovalListDTO getApprovalListByListId(String listId) {
        Grade5ScholarshipApprovalList entity = approvalListRepository.findByListId(listId)
                .orElseThrow(() -> new RuntimeException("Grade 5 Scholarship Approval list not found"));
        return toDto(entity);
    }

    public List<Grade5ScholarshipRequest> getRequestsByListId(String listId) {
        Grade5ScholarshipApprovalList entity = approvalListRepository.findByListId(listId)
                .orElseThrow(() -> new RuntimeException("Grade 5 Scholarship Approval list not found"));

        return scholarshipRepository.findAll().stream()
                .filter(r -> entity.getListId().equals(r.getApprovalListId()))
                .collect(Collectors.toList());
    }

    public String deleteApprovalList(String listId) {
        Grade5ScholarshipApprovalList entity = approvalListRepository.findByListId(listId)
                .orElseThrow(() -> new RuntimeException("Grade 5 Scholarship Approval list not found"));

        // Roll back attached requests
        List<Grade5ScholarshipRequest> requests = scholarshipRepository.findAll().stream()
                .filter(r -> entity.getListId().equals(r.getApprovalListId()))
                .collect(Collectors.toList());

        for (Grade5ScholarshipRequest request : requests) {
            String original = request.getOriginalStatus();
            if (original != null) {
                request.setStatus(original);
            } else {
                // Fallback default
                request.setStatus(entity.getType().equals("DEVIATION") 
                        ? "SUBMITTED_FOR_DEVIATION_APPROVAL" 
                        : "SUBMITTED_FOR_NORMAL_APPROVAL");
            }
            request.setApprovalListId(null);
            request.setOriginalStatus(null);
            scholarshipRepository.save(request);
        }

        approvalListRepository.delete(entity);
        return "Grade 5 Scholarship Approval list deleted successfully";
    }

    public Grade5ScholarshipApprovalListDTO processApprovalList(String listId, Grade5ScholarshipApprovalListDTO dto) {
        Grade5ScholarshipApprovalList entity = approvalListRepository.findByListId(listId)
                .orElseThrow(() -> new RuntimeException("Grade 5 Scholarship Approval list not found"));

        if (dto.getRequestDetails() == null || dto.getRequestDetails().isEmpty()) {
            throw new RuntimeException("No request approval details provided");
        }

        LocalDate actualMeetingDate = dto.getActualMeetingDate() != null ? dto.getActualMeetingDate() : LocalDate.now();
        String boardRemarks = dto.getBoardRemarks();
        String scannedReportPath = dto.getScannedReportPath();

        int approvedCount = 0;
        int rejectedCount = 0;

        for (Grade5RequestApprovalDetailDTO detail : dto.getRequestDetails()) {
            Grade5ScholarshipRequest request = scholarshipRepository.findByRequestNo(detail.getRequestNo())
                    .orElseThrow(() -> new RuntimeException("Grade 5 Scholarship Request not found: " + detail.getRequestNo()));

            String newStatus = detail.getStatus();
            if (!"APPROVED".equalsIgnoreCase(newStatus) && !"REJECTED".equalsIgnoreCase(newStatus)) {
                throw new RuntimeException("Status must be APPROVED or REJECTED for request: " + detail.getRequestNo());
            }

            request.setStatus(newStatus.toUpperCase());
            if ("REJECTED".equalsIgnoreCase(newStatus)) {
                request.setIncompleteReason(detail.getRejectReason());
                rejectedCount++;
                System.out.println("SMS & EMAIL: Grade 5 Request " + request.getRequestNo() + " rejected. Reason: " + detail.getRejectReason());
            } else {
                approvedCount++;
                System.out.println("SMS & EMAIL: Grade 5 Request " + request.getRequestNo() + " approved. Fund disbursement is underway.");
            }
            scholarshipRepository.save(request);
        }

        entity.setStatus("PROCESSED");
        entity.setProcessedAt(LocalDateTime.now());
        entity.setProcessedBy(dto.getProcessedBy() != null ? dto.getProcessedBy() : "Head Office User");
        entity.setActualMeetingDate(actualMeetingDate);
        entity.setBoardRemarks(boardRemarks);
        entity.setScannedReportPath(scannedReportPath);
        entity.setDecision(String.format("Approved: %d, Rejected: %d", approvedCount, rejectedCount));

        Grade5ScholarshipApprovalList saved = approvalListRepository.save(entity);
        return toDto(saved);
    }
}

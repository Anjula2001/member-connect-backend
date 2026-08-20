package com.memberconnect.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.MemberTerminationRequestDTO;
import com.memberconnect.backend.dto.TerminationRequestResponseDTO;
import com.memberconnect.backend.service.TerminationService;

@RestController
@RequestMapping("/api/termination-requests")
@CrossOrigin(origins = "http://localhost:3000")
public class TerminationRequestController {

    private final TerminationService terminationService;

    public TerminationRequestController(TerminationService terminationService) {
        this.terminationService = terminationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    public List<TerminationRequestResponseDTO> searchRequests(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String searchKey,
            @RequestParam(defaultValue = "requestedDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder,
            @RequestParam(required = false) List<String> locations
    ) {
        return terminationService.searchRequests(
                statuses,
                fromDate,
                toDate,
                searchKey,
                sortBy,
                sortOrder,
                locations
        );
    }

    @PostMapping("/{memberId}")
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','SUPER_ADMIN')")
    public TerminationRequestResponseDTO saveTerminationRequest(
            @PathVariable String memberId,
            @RequestBody MemberTerminationRequestDTO dto
    ) {
        return terminationService.saveRequest(memberId, dto);
    }

    @GetMapping("/member/{memberId}")
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    public List<TerminationRequestResponseDTO> getTerminationRequestsByMember(
            @PathVariable String memberId
    ) {
        return terminationService.getRequestsByMember(memberId);
    }

    @PostMapping("/{requestNo}/submit")
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','SUPER_ADMIN')")
    public TerminationRequestResponseDTO submitRequest(
            @PathVariable String requestNo
    ) {
        return terminationService.submitRequest(requestNo);
    }

    @PutMapping("/{requestNo}/mark-incomplete")
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','SUPER_ADMIN')")
    public TerminationRequestResponseDTO markIncomplete(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return terminationService.markIncomplete(requestNo, reason);
    }

    @PutMapping("/{requestNo}")
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','SUPER_ADMIN')")
    public TerminationRequestResponseDTO updateTerminationRequest(
            @PathVariable String requestNo,
            @RequestBody MemberTerminationRequestDTO dto
    ) {
        return terminationService.updateRequest(requestNo, dto);
    }

    /**
     * MMT04 manual status change. The permitted transitions and the "Inactive
     * rights" check both live in TerminationService, so they cannot be bypassed
     * by calling this endpoint directly.
     */
    @PatchMapping("/{requestNo}/status")
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    public TerminationRequestResponseDTO changeStatus(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        return terminationService.changeStatus(requestNo, body.get("targetStatus"));
    }

    /**
     * Single-request approval, kept only as an administrative escape hatch.
     *
     * The board's decisions belong on the Termination Approval List and are
     * applied together by PATCH /api/termination-approval-lists/{listId}/process,
     * which validates the whole list before writing anything. Calling this
     * endpoint per request is what used to let a half-processed list get recorded
     * as complete, so it is restricted to SUPER_ADMIN rather than left open.
     */
    @PutMapping("/{requestNo}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public TerminationRequestResponseDTO approveRequest(@PathVariable String requestNo) {
        return terminationService.approveRequest(requestNo);
    }

    /** Administrative escape hatch - see approveRequest above. */
    @PutMapping("/{requestNo}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public TerminationRequestResponseDTO rejectRequest(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return terminationService.rejectRequest(requestNo, reason);
    }
}

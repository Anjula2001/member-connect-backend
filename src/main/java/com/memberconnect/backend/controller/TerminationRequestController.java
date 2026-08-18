package com.memberconnect.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
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
    public List<TerminationRequestResponseDTO> searchRequests(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String searchKey,
            @RequestParam(defaultValue = "requestedDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder
    ) {
        return terminationService.searchRequests(
                statuses,
                fromDate,
                toDate,
                searchKey,
                sortBy,
                sortOrder
        );
    }

    @PostMapping("/{memberId}")
    public TerminationRequestResponseDTO saveTerminationRequest(
            @PathVariable String memberId,
            @RequestBody MemberTerminationRequestDTO dto
    ) {
        return terminationService.saveRequest(memberId, dto);
    }

    @GetMapping("/member/{memberId}")
    public List<TerminationRequestResponseDTO> getTerminationRequestsByMember(
            @PathVariable String memberId
    ) {
        return terminationService.getRequestsByMember(memberId);
    }

    @PostMapping("/{requestNo}/submit")
    public TerminationRequestResponseDTO submitRequest(
            @PathVariable String requestNo
    ) {
        return terminationService.submitRequest(requestNo);
    }

    @PutMapping("/{requestNo}/mark-incomplete")
    public TerminationRequestResponseDTO markIncomplete(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return terminationService.markIncomplete(requestNo, reason);
    }

    @PutMapping("/{requestNo}")
    public TerminationRequestResponseDTO updateTerminationRequest(
            @PathVariable String requestNo,
            @RequestBody MemberTerminationRequestDTO dto
    ) {
        return terminationService.updateRequest(requestNo, dto);
    }

    @PutMapping("/{requestNo}/approve")
    public TerminationRequestResponseDTO approveRequest(@PathVariable String requestNo) {
        return terminationService.approveRequest(requestNo);
    }

    @PutMapping("/{requestNo}/reject")
    public TerminationRequestResponseDTO rejectRequest(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return terminationService.rejectRequest(requestNo, reason);
    }
}

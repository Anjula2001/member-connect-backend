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

import com.memberconnect.backend.dto.MemberRetirementRequestDTO;
import com.memberconnect.backend.dto.RetirementRequestResponseDTO;
import com.memberconnect.backend.service.RetirementService;

@RestController
@RequestMapping("/api/retirement-requests")
@CrossOrigin(origins = "http://localhost:3000")
public class RetirementRequestController {

    private final RetirementService retirementService;

    public RetirementRequestController(RetirementService retirementService) {
        this.retirementService = retirementService;
    }

    // Get all retirement requests
    @GetMapping
    public List<RetirementRequestResponseDTO> searchRequests(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String searchKey,
            @RequestParam(defaultValue = "requestedDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder
    ) {
        return retirementService.searchRequests(
                statuses,
                fromDate,
                toDate,
                searchKey,
                sortBy,
                sortOrder
        );
    }

    // Save retirement request
    @PostMapping("/{memberId}")
    public RetirementRequestResponseDTO saveRetirementRequest(
            @PathVariable String memberId,
            @RequestBody MemberRetirementRequestDTO dto
    ) {
        return retirementService.saveRequest(memberId, dto);
    }
    
    // Get retirement requests by member
    @GetMapping("/member/{memberId}")
    public List<RetirementRequestResponseDTO> getRetirementRequestsByMember(
            @PathVariable String memberId
    ) {
        return retirementService.getRequestsByMember(memberId);
    }
 
    // Submit retirement request
    @PostMapping("/{requestNo}/submit")
    public RetirementRequestResponseDTO submitRequest(
            @PathVariable String requestNo
    ) {
        return retirementService.submitRequest(requestNo);
    }

    // Mark incomplete
    @PutMapping("/{requestNo}/mark-incomplete")
    public RetirementRequestResponseDTO markIncomplete(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return retirementService.markIncomplete(requestNo, reason);
    }

    // Approve request
    @PutMapping("/{requestNo}/approve")
    public RetirementRequestResponseDTO approveRequest(
            @PathVariable String requestNo
    ) {
        return retirementService.approveRequest(requestNo);
    }

    // Reject request
    @PutMapping("/{requestNo}/reject")
    public RetirementRequestResponseDTO rejectRequest(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return retirementService.rejectRequest(requestNo, reason);
    }

    // Change retirement request status
    @PutMapping("/{requestNo}/status")
    public RetirementRequestResponseDTO changeRetirementRequestStatus(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        String status = body.get("status");
        return retirementService.changeRequestStatus(requestNo, status);
    }

    @GetMapping("/request/{id}")
    public RetirementRequestResponseDTO getRetirementRequestById(
            @PathVariable String id
    ) {
        return retirementService.getRequestByRequestNo(id);
    }

    // Update retirement request
    @PutMapping("/{requestNo}")
    public RetirementRequestResponseDTO updateRetirementRequest(
            @PathVariable String requestNo,
            @RequestBody MemberRetirementRequestDTO dto
    ) {
        return retirementService.updateRequest(requestNo, dto);
    }

}

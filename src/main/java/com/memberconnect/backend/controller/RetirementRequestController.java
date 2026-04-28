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
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.MemberRetirementRequestDTO;
import com.memberconnect.backend.dto.RetirementRequestResponseDTO;
import com.memberconnect.backend.model.RetirementRequest;
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
    public List<RetirementRequest> getAllRetirementRequests() {
        return retirementService.getAllRequests();
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
    @PostMapping("/{requestId}/submit")
    public RetirementRequestResponseDTO submitRequest(
            @PathVariable Long requestId
    ) {
        return retirementService.submitRequest(requestId);
    }

    // Mark incomplete
    @PutMapping("/{requestId}/mark-incomplete")
    public RetirementRequestResponseDTO markIncomplete(
            @PathVariable Long requestId,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return retirementService.markIncomplete(requestId, reason);
    }
}
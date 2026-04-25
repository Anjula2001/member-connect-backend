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
import com.memberconnect.backend.dto.MemberRetirementValidationDTO;
import com.memberconnect.backend.dto.MemberSummaryDTO;
import com.memberconnect.backend.dto.RetirementRequestResponseDTO;
import com.memberconnect.backend.service.RetirementService;

@RestController
@RequestMapping("/api/members")
@CrossOrigin(origins = "http://localhost:3000")
public class MemberController {

    private final RetirementService retirementService;

    public MemberController(RetirementService retirementService) {
        this.retirementService = retirementService;
    }

    @GetMapping("/{memberId}")
    public MemberSummaryDTO getMember(@PathVariable("memberId") String memberId) {
        return retirementService.getMemberSummary(memberId);
    }

    @GetMapping("/{memberId}/retirement-validation")
    public MemberRetirementValidationDTO validateMemberForRetirement(
            @PathVariable("memberId") String memberId
    ) {
        return retirementService.validateMemberForRetirement(memberId);
    }

    @PostMapping("/{memberId}/retirement-requests")
    public RetirementRequestResponseDTO saveRetirementRequest(
            @PathVariable("memberId") String memberId,
            @RequestBody MemberRetirementRequestDTO dto
    ) {
        return retirementService.saveRequest(memberId, dto);
    }

    @GetMapping("/{memberId}/retirement-requests")
    public List<RetirementRequestResponseDTO> getRetirementRequestsByMember(
            @PathVariable("memberId") String memberId
    ) {
        return retirementService.getRequestsByMember(memberId);
    }

    @PostMapping("/retirement-requests/{requestId}/submit")
    public RetirementRequestResponseDTO submitRequest(
            @PathVariable Long requestId
    ) {
        return retirementService.submitRequest(requestId);
    }

    @PutMapping("/retirement-requests/{requestId}/mark-incomplete")
    public RetirementRequestResponseDTO markIncomplete(
            @PathVariable Long requestId,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return retirementService.markIncomplete(requestId, reason);
    }
}
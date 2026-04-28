package com.memberconnect.backend.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.MemberRetirementValidationDTO;
import com.memberconnect.backend.dto.MemberSummaryDTO;
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
    public MemberSummaryDTO getMember(@PathVariable String memberId) {
        return retirementService.getMemberSummary(memberId);
    }

    @GetMapping("/{memberId}/retirement-validation")
    public MemberRetirementValidationDTO validateMemberForRetirement(
            @PathVariable String memberId
    ) {
        return retirementService.validateMemberForRetirement(memberId);
    }
}
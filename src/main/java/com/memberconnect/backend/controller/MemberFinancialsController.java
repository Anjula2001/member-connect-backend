package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.MemberFinancialsDTO;
import com.memberconnect.backend.service.MemberFinancialsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member-financials")
@CrossOrigin
public class MemberFinancialsController {

    @Autowired
    private MemberFinancialsService service;

    /** Read is available to everyone who can view a membership profile. */
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','ACCOUNTS','SUPER_ADMIN')")
    @GetMapping("/{memberId}")
    public MemberFinancialsDTO getFinancials(@PathVariable Long memberId) {
        return service.getFinancials(memberId);
    }

    /**
     * Manual entry, pending the Finance Module. Restricted to Accounts and Super
     * Admin — the same ownership as the Remittance Master, since these are finance
     * figures rather than membership administration.
     */
    @PreAuthorize("hasAnyRole('ACCOUNTS','SUPER_ADMIN')")
    @PutMapping("/{memberId}")
    public MemberFinancialsDTO updateFinancials(
            @PathVariable Long memberId,
            @RequestBody MemberFinancialsDTO request) {
        return service.updateFinancials(memberId, request);
    }
}

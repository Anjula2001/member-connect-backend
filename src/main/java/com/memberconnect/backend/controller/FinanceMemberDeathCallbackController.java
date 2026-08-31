package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.MemberDeathRecordDTO;
import com.memberconnect.backend.service.MemberDeathRecordService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound edge for the Finance Module (SRS MMT25).
 *
 * The SRS puts the final status change in Finance's hands: "Once the required
 * activities from the Finance Module is done for each Member Record, the status
 * of each Member will be made Deceased. (This status change process will be
 * handled from the Finance Module)". This is where Finance says so.
 *
 * Restricted to ACCOUNTS so the Finance Module authenticates as a service user
 * rather than the endpoint being left open, and SUPER_ADMIN so the flow remains
 * exercisable before the real Finance Module exists.
 */
@RestController
@RequestMapping("/api/finance/member-deaths")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasAnyRole('ACCOUNTS','SUPER_ADMIN')")
public class FinanceMemberDeathCallbackController {

    private final MemberDeathRecordService memberDeathRecordService;

    public FinanceMemberDeathCallbackController(MemberDeathRecordService memberDeathRecordService) {
        this.memberDeathRecordService = memberDeathRecordService;
    }

    /**
     * Finance reports that a deceased member's accounts are closed and the funds
     * disbursed. Moves the member from MEMBER_DEATH_APPROVED to DECEASED and
     * notifies the nominee.
     *
     * Safe to retry: a repeat call for a member who is already DECEASED returns
     * the current state without notifying a second time.
     */
    @PatchMapping("/{recordNo}/complete")
    public MemberDeathRecordDTO completeMemberDeath(@PathVariable String recordNo) {
        return memberDeathRecordService.completeMemberDeath(recordNo);
    }
}

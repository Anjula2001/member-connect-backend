package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.AuditDTO;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Read-only history feed backing the Progress tabs (spec 4.2 and 4.8).
 */
@RestController
@RequestMapping("/api/audit")
@CrossOrigin
// ACCOUNTS is included because it can edit a member's remittance and account
// details — being able to make an entry but not see its history would be a gap.
@PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','ACCOUNTS','SUPER_ADMIN')")
public class AuditController {

    @Autowired
    private AuditService auditService;

    @Autowired
    private MemberRepository memberRepository;

    /**
     * Newest audit entries across all modules, for the dashboard's Recent Activity card.
     * Read-only and already covered by the class-level role check.
     */
    @GetMapping("/recent")
    public List<AuditDTO> getRecentActivity(
            @RequestParam(required = false, defaultValue = "5") int limit) {
        return auditService.getRecentActivity(limit);
    }

    @GetMapping("/application/{applicationId}")
    public List<AuditDTO> getApplicationHistory(@PathVariable Long applicationId) {
        return auditService.getApplicationHistory(applicationId);
    }

    /**
     * A member's history includes the application it was created from, so the
     * originating application id is resolved here rather than by the caller.
     */
    @GetMapping("/member/{memberId}")
    public List<AuditDTO> getMemberHistory(@PathVariable Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        Long applicationId = member.getApplication() == null ? null : member.getApplication().getId();
        return auditService.getMemberHistory(memberId, applicationId);
    }
}

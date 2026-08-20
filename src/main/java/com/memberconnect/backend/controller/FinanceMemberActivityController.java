package com.memberconnect.backend.controller;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.MemberActivityDTO;
import com.memberconnect.backend.service.MemberActivityService;

/**
 * Where the Finance Module reports account activity, which is the authoritative
 * input to the dormant identification process (SRS 4.2.1).
 *
 * This endpoint exists because of an honest gap. SRS 4.2.1 defines dormancy by
 * accounts "not updated for the specified period", and the evaluation notes make
 * that a remittance - but there is no remittance-posting flow anywhere in
 * MemberConnect. RemittanceMasterAccount holds amount rules, Loan and
 * LoanObligation are read-only stubs, and the Finance Module does not exist yet.
 * Every in-app signal MemberConnect could hook is therefore a proxy. Without this
 * endpoint MMD10 cannot be correct in production no matter how many proxies are
 * added, so it is the one integration the feature genuinely depends on.
 *
 * Restricted to ACCOUNTS, which owns the Finance edge, and SUPER_ADMIN. It is
 * deliberately NOT open to the offices: activity is a fact reported by the system
 * that holds the accounts, not something a clerk asserts.
 *
 * Idempotent - MemberActivityService never moves the date backwards - so Finance
 * can replay a month safely after an outage.
 */
@RestController
@RequestMapping("/api/finance/members")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasAnyRole('ACCOUNTS','SUPER_ADMIN')")
public class FinanceMemberActivityController {

    private final MemberActivityService memberActivityService;

    public FinanceMemberActivityController(MemberActivityService memberActivityService) {
        this.memberActivityService = memberActivityService;
    }

    @PostMapping("/{memberId}/activity")
    public ResponseEntity<Map<String, Object>> recordActivity(
            @PathVariable String memberId,
            @RequestBody(required = false) MemberActivityDTO dto
    ) {
        LocalDate activityDate = dto != null && dto.getActivityDate() != null
                ? dto.getActivityDate()
                : LocalDate.now();
        String source = dto != null && dto.getSource() != null ? dto.getSource() : "FINANCE";

        memberActivityService.recordActivity(memberId, activityDate, source);

        return ResponseEntity.ok(Map.of(
                "memberId", memberId,
                "activityDate", activityDate.toString(),
                "source", source
        ));
    }
}

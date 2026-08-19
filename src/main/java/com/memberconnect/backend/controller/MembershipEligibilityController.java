package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.MembershipEligibilityConfigDTO;
import com.memberconnect.backend.service.MembershipEligibilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/membership-eligibility")
@CrossOrigin
public class MembershipEligibilityController {

    @Autowired
    private MembershipEligibilityService service;

    // The registration form shows the permitted age range alongside Date of Birth,
    // so reading is open to everyone who works with registrations.
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @GetMapping
    public MembershipEligibilityConfigDTO getConfig() {
        return service.getConfigDto();
    }

    // Who is allowed to join is a membership-policy setting, not a finance one —
    // Super Admin only (deliberately NOT Accounts, unlike the Remittance Master).
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping
    public MembershipEligibilityConfigDTO updateConfig(@RequestBody MembershipEligibilityConfigDTO dto) {
        return service.updateConfig(dto);
    }
}

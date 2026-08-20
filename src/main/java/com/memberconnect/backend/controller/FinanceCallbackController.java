package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.TerminationRequestResponseDTO;
import com.memberconnect.backend.service.TerminationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound edge for the Finance Module (SRS MMT11).
 *
 * The SRS puts the final status change in Finance's hands: "Once the required
 * activities from the Finance Module is done for each Member Record, the status
 * of each Member will be made Terminated. (This status change process will be
 * handled from the Finance Module)". This is where Finance says so.
 *
 * Restricted to ACCOUNTS so the Finance Module authenticates as a service user
 * rather than the endpoint being left open, and SUPER_ADMIN so the flow remains
 * exercisable before the real Finance Module exists.
 */
@RestController
@RequestMapping("/api/finance/terminations")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasAnyRole('ACCOUNTS','SUPER_ADMIN')")
public class FinanceCallbackController {

    private final TerminationService terminationService;

    public FinanceCallbackController(TerminationService terminationService) {
        this.terminationService = terminationService;
    }

    /**
     * Finance reports that a terminated member's accounts are closed. Moves the
     * member from TERMINATION_APPROVED to TERMINATED and notifies them.
     *
     * Safe to retry: a repeat call for a member who is already TERMINATED
     * returns the current state without notifying a second time.
     */
    @PatchMapping("/{requestNo}/complete")
    public TerminationRequestResponseDTO completeTermination(@PathVariable String requestNo) {
        return terminationService.completeTermination(requestNo);
    }
}

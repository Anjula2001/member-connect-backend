package com.memberconnect.backend.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.TerminationReasonMasterDto;
import com.memberconnect.backend.service.TerminationReasonMasterService;

/**
 * Termination Request Master - maintenance of the Termination Reasons Master (MMT01).
 *
 * Super Admin only, mirroring UniversityMasterController. The reasons themselves are
 * read by every role that raises a termination request, but through
 * /api/masters/termination-reasons, which serves the active ones and nothing else.
 *
 * No delete mapping, deliberately - see TerminationReasonMasterService.
 */
@RestController
@RequestMapping("/api/admin/termination-reason-master")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TerminationReasonMasterController {

    private final TerminationReasonMasterService service;

    public TerminationReasonMasterController(TerminationReasonMasterService service) {
        this.service = service;
    }

    @GetMapping("/reasons")
    public List<TerminationReasonMasterDto> getReasons() {
        return service.getReasons();
    }

    @PostMapping("/reasons")
    public TerminationReasonMasterDto createReason(@RequestBody TerminationReasonMasterDto request) {
        return service.createReason(request);
    }

    @PutMapping("/reasons/{id}")
    public TerminationReasonMasterDto updateReason(@PathVariable Long id,
                                                   @RequestBody TerminationReasonMasterDto request) {
        return service.updateReason(id, request);
    }
}

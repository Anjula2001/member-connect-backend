package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.dto.NicValidationResponseDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.service.MemberApplicationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/applications")
@CrossOrigin
public class MemberApplicationController {

    @Autowired
    private MemberApplicationService service;

    // Only District Office staff (who take the applicant's details at the counter) and
    // Super Admin can create a new registration.
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','SUPER_ADMIN')")
    @PostMapping("/createApplication")
    public MemberApplicationDTO createMemberApplication(@RequestBody MemberApplicationDTO memberApplicationDTO) {
        return service.saveMemberApplication(memberApplicationDTO);
    }

    // Search/browse is shared by every role that touches the registration process.
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @GetMapping("/getApplication")
    public List<MemberApplicationDTO> getUser(){
        return service.getAllMemberApplications();
    }

    /**
     * Server-side filtered/sorted search for the New Member Registration List.
     * All parameters optional. Replaces pulling the whole table into the browser.
     */
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @GetMapping("/search")
    public List<MemberApplicationDTO> searchApplications(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<ApplicationStatus> statuses,
            @RequestParam(required = false) List<String> locations,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedTo,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection) {
        return service.searchApplications(
                query, statuses, locations, receivedFrom, receivedTo, sortBy, sortDirection);
    }

    // Full edit is District Office only (they own the application until it's submitted);
    // Head Office/Board Secretary make status-only changes via the partial-update endpoint.
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','SUPER_ADMIN')")
    @PutMapping("/updateApplication/{id}")
    public MemberApplicationDTO updateMemberApplication(
            @PathVariable Long id,
            @RequestBody MemberApplicationDTO memberApplicationDTO) {

        return service.updateMemberApplication(id, memberApplicationDTO);
    }

    // Shared: District Office edits their own un-submitted applications; Head
    // Office/Board Secretary use this to record Approve/Reject decisions per application.
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @PatchMapping("/updateApplicationPartial/{id}")
    public MemberApplicationDTO updatePartial(
            @PathVariable Long id,
            @RequestBody MemberApplicationDTO dto) {

        return service.updatePartial(id, dto);
    }

    // Hard delete isn't a function defined anywhere in the spec — restrict to Super Admin.
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/deleteApplication/{id}")
    public String deleteMemberApplication(@PathVariable Long id) {
        return service.deleteMemberApplication(id);
    }

    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @GetMapping("/{id}")
    public MemberApplicationDTO getApplicationById(@PathVariable Long id) {
        return service.getApplicationById(id);
    }

    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @GetMapping("/nic/{nic}")
    public MemberApplicationDTO getApplicationByNic(@PathVariable String nic) {
        return service.getApplicationByNic(nic);
    }

    // District Office needs this for the New -> Submitted for Approval transition (the
    // "Submit" button). Setting status to INACTIVE specifically requires the "Inactive
    // rights" the spec calls out for Head Office/Board Secretary/Super Admin only — that
    // extra check lives in MemberApplicationService.updateStatus(), since it depends on
    // the *target* status, not just who's calling.
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @PatchMapping("/{id}/status")
    public MemberApplicationDTO updateStatus(
            @PathVariable Long id,
            @RequestParam ApplicationStatus status) {
        return service.updateStatus(id, status);
    }

    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','SUPER_ADMIN')")
    @GetMapping("/validate-nic")
    public NicValidationResponseDTO validateNic(
            @RequestParam String nicNumber,
            @RequestParam(required = false) Long excludeApplicationId) {
        return service.validateNic(nicNumber, excludeApplicationId);
    }

}

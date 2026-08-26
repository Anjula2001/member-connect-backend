package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.MemberDTO;
import com.memberconnect.backend.dto.MemberRetirementValidationDTO;
import com.memberconnect.backend.dto.MemberSummaryDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.service.MemberDeathRecordService;
import com.memberconnect.backend.service.MemberService;
import com.memberconnect.backend.service.RetirementService;
import com.memberconnect.backend.service.TerminationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.memberconnect.backend.enums.MembershipDocumentType;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/members")
@CrossOrigin(origins = "http://localhost:3000")
public class MemberController {

    @Autowired
    private MemberService memberService;

    private final RetirementService retirementService;
    private final TerminationService terminationService;
    private final MemberDeathRecordService memberDeathRecordService;

    public MemberController(
            RetirementService retirementService,
            TerminationService terminationService,
            MemberDeathRecordService memberDeathRecordService) {
        this.retirementService = retirementService;
        this.terminationService = terminationService;
        this.memberDeathRecordService = memberDeathRecordService;
    }

    // Member records are only ever created from an approved Board Approval List.
    @PreAuthorize("hasAnyRole('HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @PostMapping("/createMember")
    public MemberDTO createMember(@RequestBody MemberDTO memberDTO) {
        return memberService.saveMember(memberDTO);
    }

    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE',"
            + "'HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @GetMapping("/getMembers")
    public List<MemberDTO> getAllMembers() {
        return memberService.getAllMembers();
    }

    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE',"
            + "'HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @GetMapping("/getMemberById/{id}")
    public ResponseEntity<MemberDTO> getMemberById(@PathVariable Long id) {
        return ResponseEntity.ok(memberService.getMemberById(id));
    }

    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE',"
            + "'HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @GetMapping("/by-member-id/{memberId}")
    public ResponseEntity<MemberDTO> getMemberByMemberId(@PathVariable String memberId) {
        return ResponseEntity.ok(memberService.getMemberByMemberId(memberId));
    }

    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE',"
            + "'HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @GetMapping("/getMemberByNic/{nic}")
    public ResponseEntity<MemberDTO> getMemberByNic(@PathVariable String nic) {
        return ResponseEntity.ok(memberService.getMemberByNic(nic));
    }

    
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE',"
            + "'HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @GetMapping("/search")
    public List<MemberDTO> searchMembers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<MemberStatus> statuses,
            @RequestParam(required = false) List<String> locations,
            @RequestParam(required = false) String workingLocationType,
            @RequestParam(required = false) String educationalZone,
            @RequestParam(required = false) String educationalDistrict,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate membershipStartFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate membershipStartTo,
            // MR15/16/17: "Members without <document>" - applied here so the print
            // screens stop fetching the whole active membership to discard most of it.
            @RequestParam(required = false) MembershipDocumentType withoutDocument,
            // MR15/16/17 Board Meeting Date period. Omitting both is the spec's "Any".
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate boardMeetingFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate boardMeetingTo,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection) {
        return memberService.searchMembers(query, statuses, locations, workingLocationType, educationalZone,
                educationalDistrict, membershipStartFrom, membershipStartTo,
                withoutDocument, boardMeetingFrom, boardMeetingTo, sortBy, sortDirection);
    }

    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @PutMapping("/updateMember/{id}")
    public MemberDTO updateMember(
            @PathVariable Long id,
            @RequestBody MemberDTO memberDTO) {
        return memberService.updateMember(id, memberDTO);
    }

    // Not a defined business function anywhere in the spec — admin-only safety valve.
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/deleteMember/{id}")
    public String deleteMember(@PathVariable Long id) {
        return memberService.deleteMember(id);
    }

    // Real activation is meant to come from the Finance Module (out of scope here). Until
    // that integration exists, MemberService.updateStatus() restricts the ACTIVE target
    // specifically to Super Admin as a clearly-labelled testing-only override.
    @PreAuthorize("hasAnyRole('HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @PatchMapping("/{id}/status")
    public MemberDTO updateStatus(
            @PathVariable Long id,
            @RequestParam MemberStatus status) {
        return memberService.updateStatus(id, status);
    }

    @Autowired
    private com.memberconnect.backend.repository.LoanRepository loanRepository;

    @Autowired
    private com.memberconnect.backend.repository.LoanObligationRepository loanObligationRepository;

    // Get member summary information
    @GetMapping("/{memberId}")
    public MemberSummaryDTO getMember(@PathVariable String memberId) {
        return retirementService.getMemberSummary(memberId);
    }

    // Validate a member for retirement
    @PreAuthorize("hasAuthority('RET_REQUEST_VIEW')")
    @GetMapping("/{memberId}/retirement-validation")
    public MemberRetirementValidationDTO validateMemberForRetirement(
            @PathVariable String memberId) {
        return retirementService.validateMemberForRetirement(memberId);
    }

    // Validate a member for termination
    @GetMapping("/{memberId}/termination-validation")
    public MemberRetirementValidationDTO validateMemberForTermination(
            @PathVariable String memberId) {
        return terminationService.validateMemberForTermination(memberId);
    }

   // Validate a member for death record
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE',"
            + "'HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @GetMapping("/{memberId}/member-death-validation")
    public MemberRetirementValidationDTO validateMemberForDeathRecord(
            @PathVariable String memberId) {
        return memberDeathRecordService.validateMemberForDeathRecord(memberId);
    }

    // Get member loans and obligations
    @GetMapping("/{memberId}/loans")
    public ResponseEntity<?> getMemberLoans(@PathVariable String memberId) {
        List<com.memberconnect.backend.model.Loan> loans = loanRepository.findByMemberId(memberId);
        List<com.memberconnect.backend.model.LoanObligation> obligations = loanObligationRepository
                .findByMemberId(memberId);

        return ResponseEntity.ok(java.util.Map.of(
                "loans", loans,
                "obligations", obligations));
    }

    /**
     * Row count for the dashboard, so a counter does not have to download the rows.
     * Inherits the same authorization as the listing beside it.
     */
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE',"
            + "'HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
    @GetMapping("/count")
    public java.util.Map<String, Long> countMembers(
            @RequestParam(required = false) java.util.List<String> locations) {
        return java.util.Map.of("count", memberService.countMembers(locations));
    }
}

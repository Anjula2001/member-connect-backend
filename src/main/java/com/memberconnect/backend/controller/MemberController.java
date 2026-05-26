package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.MemberDTO;
import com.memberconnect.backend.dto.MemberRetirementValidationDTO;
import com.memberconnect.backend.dto.MemberSummaryDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.service.MemberService;
import com.memberconnect.backend.service.RetirementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@CrossOrigin(origins = "http://localhost:3000")
public class MemberController {

    @Autowired
    private MemberService memberService;

    private final RetirementService retirementService;

    public MemberController(RetirementService retirementService) {
        this.retirementService = retirementService;
    }

    @PostMapping("/createMember")
    public MemberDTO createMember(@RequestBody MemberDTO memberDTO) {
        return memberService.saveMember(memberDTO);
    }

    @GetMapping("/getMembers")
    public List<MemberDTO> getAllMembers() {
        return memberService.getAllMembers();
    }

    @GetMapping("/getMemberById/{id}")
    public ResponseEntity<MemberDTO> getMemberById(@PathVariable Long id) {
        return ResponseEntity.ok(memberService.getMemberById(id));
    }

    @GetMapping("/getMemberByNic/{nic}")
    public ResponseEntity<MemberDTO> getMemberByNic(@PathVariable String nic) {
        return ResponseEntity.ok(memberService.getMemberByNic(nic));
    }

    /**
     * Flexible search endpoint used by the directory page.
     * All parameters are optional.
     * GET /api/members/search?query=&statuses=ACTIVE,INACTIVE&locations=Colombo&workingLocationType=school&educationalZone=colombo-zone
     */
    @GetMapping("/search")
    public List<MemberDTO> searchMembers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<MemberStatus> statuses,
            @RequestParam(required = false) List<String> locations,
            @RequestParam(required = false) String workingLocationType,
            @RequestParam(required = false) String educationalZone) {
        return memberService.searchMembers(query, statuses, locations, workingLocationType, educationalZone);
    }

    @PutMapping("/updateMember/{id}")
    public MemberDTO updateMember(
            @PathVariable Long id,
            @RequestBody MemberDTO memberDTO) {
        return memberService.updateMember(id, memberDTO);
    }

    @DeleteMapping("/deleteMember/{id}")
    public String deleteMember(@PathVariable Long id) {
        return memberService.deleteMember(id);
    }

    @PatchMapping("/{id}/status")
    public MemberDTO updateStatus(
            @PathVariable Long id,
            @RequestParam MemberStatus status) {
        return memberService.updateStatus(id, status);
    }

    // Get member summary information
    @GetMapping("/{memberId}")
    public MemberSummaryDTO getMember(@PathVariable String memberId) {
        return retirementService.getMemberSummary(memberId);
    }

    // Validate a member for retirement
    @GetMapping("/{memberId}/retirement-validation")
    public MemberRetirementValidationDTO validateMemberForRetirement(
            @PathVariable String memberId
    ) {
        return retirementService.validateMemberForRetirement(memberId);
    }
}

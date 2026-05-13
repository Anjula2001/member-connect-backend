package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.MemberTerminationDTO;
import com.memberconnect.backend.enums.TerminationStatus;
import com.memberconnect.backend.service.MemberTerminationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/terminations")
@CrossOrigin
public class MemberTerminationController {

    @Autowired
    private MemberTerminationService memberTerminationService;

    // Create new termination request
    @PostMapping("/create")
    public ResponseEntity<?> createTermination(@RequestBody MemberTerminationDTO dto) {
        try {
            MemberTerminationDTO result = memberTerminationService.createTermination(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    // Get termination by ID
    @GetMapping("/getTerminationById/{id}")
    public ResponseEntity<MemberTerminationDTO> getTerminationById(@PathVariable Long id) {
        try {
            MemberTerminationDTO result = memberTerminationService.getTerminationById(id);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    // Get termination by Termination ID
    @GetMapping("/getTerminationByTerminationId/{terminationId}")
    public ResponseEntity<MemberTerminationDTO> getTerminationByTerminationId(@PathVariable String terminationId) {
        try {
            MemberTerminationDTO result = memberTerminationService.getTerminationByTerminationId(terminationId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    // Get all terminations for a member
    @GetMapping("/getTerminationsByMemberId/{memberId}")
    public ResponseEntity<List<MemberTerminationDTO>> getTerminationsByMemberId(@PathVariable Long memberId) {
        try {
            List<MemberTerminationDTO> result = memberTerminationService.getTerminationsByMemberId(memberId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    // Get all pending terminations
    @GetMapping("/getPendingTerminations")
    public ResponseEntity<List<MemberTerminationDTO>> getPendingTerminations() {
        List<MemberTerminationDTO> result = memberTerminationService.getPendingTerminations();
        return ResponseEntity.ok(result);
    }

    // Get all terminations by status
    @GetMapping("/getTerminationsByStatus/{status}")
    public ResponseEntity<List<MemberTerminationDTO>> getTerminationsByStatus(@PathVariable TerminationStatus status) {
        List<MemberTerminationDTO> result = memberTerminationService.getTerminationsByStatus(status);
        return ResponseEntity.ok(result);
    }

    // Get all terminations
    @GetMapping("/getAllTerminations")
    public ResponseEntity<List<MemberTerminationDTO>> getAllTerminations() {
        List<MemberTerminationDTO> result = memberTerminationService.getAllTerminations();
        return ResponseEntity.ok(result);
    }

    // Approve a termination
    @PutMapping("/approveTermination/{id}")
    public ResponseEntity<MemberTerminationDTO> approveTermination(
            @PathVariable Long id,
            @RequestParam String approvedBy) {
        try {
            MemberTerminationDTO result = memberTerminationService.approveTermination(id, approvedBy);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // Reject a termination
    @PutMapping("/rejectTermination/{id}")
    public ResponseEntity<MemberTerminationDTO> rejectTermination(
            @PathVariable Long id,
            @RequestParam String remarks) {
        try {
            MemberTerminationDTO result = memberTerminationService.rejectTermination(id, remarks);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // Process a termination
    @PutMapping("/processTermination/{id}")
    public ResponseEntity<MemberTerminationDTO> processTermination(
            @PathVariable Long id,
            @RequestParam String processedBy) {
        try {
            MemberTerminationDTO result = memberTerminationService.processTermination(id, processedBy);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // Update termination
    @PutMapping("/updateTermination/{id}")
    public ResponseEntity<MemberTerminationDTO> updateTermination(
            @PathVariable Long id,
            @RequestBody MemberTerminationDTO dto) {
        try {
            MemberTerminationDTO result = memberTerminationService.updateTermination(id, dto);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // Delete termination
    @DeleteMapping("/deleteTermination/{id}")
    public ResponseEntity<String> deleteTermination(@PathVariable Long id) {
        try {
            memberTerminationService.deleteTermination(id);
            return ResponseEntity.ok("Termination deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}

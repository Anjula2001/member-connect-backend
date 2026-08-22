package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.MemberTransferDto;
import com.memberconnect.backend.model.MemberTransferRequest;
import com.memberconnect.backend.service.MemberTransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/member-transfers")
@CrossOrigin(origins = "http://localhost:3000")
public class MemberTransferController {

    private final MemberTransferService memberTransferService;

    public MemberTransferController(MemberTransferService memberTransferService) {
        this.memberTransferService = memberTransferService;
    }

    // Endpoint to get all member transfer requests
    @GetMapping
    public List<MemberTransferRequest> getAllRequests() {
        return memberTransferService.getAllRequests();
    }

    // Endpoint to get a specific member transfer request by ID
    @GetMapping("/{id}")
    public MemberTransferRequest getRequestById(@PathVariable Long id) {
        return memberTransferService.getRequestById(id);
    }

    // Endpoint to submit a new member transfer request
    @PostMapping("/submit")
    public ResponseEntity<?> submitRequest(@RequestBody MemberTransferDto dto) {
        try {
            return ResponseEntity.ok(memberTransferService.submitRequest(dto));
        } catch (IllegalStateException e) {
            // A request is already awaiting approval for this member - a conflict the
            // caller can act on, not a server fault
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage() != null
                            ? e.getMessage()
                            : "Failed to submit member transfer request"));
        }
    }

    // Endpoint to check whether a member already has a transfer request awaiting
    // approval, so the UI can refuse a new one before the form is filled in
    @GetMapping("/in-flight/{memberId}")
    public Map<String, Object> getRequestAwaitingApproval(@PathVariable String memberId) {
        MemberTransferRequest existing = memberTransferService.findRequestAwaitingApproval(memberId);

        Map<String, Object> response = new HashMap<>();
        response.put("hasInFlight", existing != null);
        response.put("requestId", existing != null ? existing.getRequestId() : null);
        return response;
    }

    // Endpoint to approve a member transfer request
    @PostMapping("/approve/{requestId}")
    public MemberTransferRequest approveRequest(@PathVariable String requestId) {
        return memberTransferService.approveRequest(requestId);
    }

    // Endpoint to reject a member transfer request
    @PostMapping("/reject/{requestId}")
    public MemberTransferRequest rejectRequest(
            @PathVariable String requestId,
            @RequestBody java.util.Map<String, String> body
    ) {
        String reason = body.get("decisionReason");
        return memberTransferService.rejectRequest(requestId, reason);
    }
}
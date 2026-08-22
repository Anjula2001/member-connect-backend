package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.MemberTransferDto;
import com.memberconnect.backend.model.MemberTransferRequest;
import com.memberconnect.backend.service.MemberTransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Member Transfers (MMC27-MMC30).
 *
 * Rights are named per endpoint rather than per role, matching the scholarship
 * controllers: raising a transfer belongs to the District Office, deciding one to Head
 * Office, and the View Mode status change to whoever holds "Inactive rights". The
 * service applies the location scope on top, so a District Office user sees only their
 * own office's requests even though the right itself is national.
 */
@RestController
@RequestMapping("/api/member-transfers")
@CrossOrigin(origins = "http://localhost:3000")
public class MemberTransferController {

    private final MemberTransferService memberTransferService;

    public MemberTransferController(MemberTransferService memberTransferService) {
        this.memberTransferService = memberTransferService;
    }

    // Endpoint to get all member transfer requests, scoped to the caller's office
    @PreAuthorize("hasAuthority('MT_REQUEST_VIEW')")
    @GetMapping
    public List<MemberTransferRequest> getAllRequests(
            @RequestParam(name = "locations", required = false) List<String> locations
    ) {
        return memberTransferService.getAllRequests(locations);
    }

    // Endpoint to get a specific member transfer request by ID
    @PreAuthorize("hasAuthority('MT_REQUEST_VIEW')")
    @GetMapping("/{id}")
    public MemberTransferRequest getRequestById(@PathVariable Long id) {
        return memberTransferService.getRequestById(id);
    }

    // Endpoint to submit a new member transfer request
    @PreAuthorize("hasAuthority('MT_REQUEST_CREATE')")
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
    // approval, so the UI can refuse a new one before the form is filled in.
    // Read by both the profile Actions menu and the entry form, so it asks only for
    // view rights rather than the right to create.
    @PreAuthorize("hasAuthority('MT_REQUEST_VIEW')")
    @GetMapping("/in-flight/{memberId}")
    public Map<String, Object> getRequestAwaitingApproval(@PathVariable String memberId) {
        MemberTransferRequest existing = memberTransferService.findRequestAwaitingApproval(memberId);

        Map<String, Object> response = new HashMap<>();
        response.put("hasInFlight", existing != null);
        response.put("requestId", existing != null ? existing.getRequestId() : null);
        return response;
    }

    // Endpoint to approve a member transfer request
    @PreAuthorize("hasAuthority('MT_REQUEST_APPROVE')")
    @PostMapping("/approve/{requestId}")
    public MemberTransferRequest approveRequest(@PathVariable String requestId) {
        return memberTransferService.approveRequest(requestId);
    }

    // Endpoint to change a request's status from View Mode. The only transition it
    // allows ends at Inactive, so one fixed right covers it.
    @PreAuthorize("hasAuthority('MT_REQUEST_SET_INACTIVE')")
    @PutMapping("/{requestId}/status")
    public ResponseEntity<?> changeRequestStatus(
            @PathVariable String requestId,
            @RequestBody Map<String, String> body
    ) {
        try {
            return ResponseEntity.ok(
                    memberTransferService.changeRequestStatus(requestId, body.get("status")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage() != null
                            ? e.getMessage()
                            : "Failed to change the request status"));
        }
    }

    // Endpoint to reject a member transfer request
    @PreAuthorize("hasAuthority('MT_REQUEST_APPROVE')")
    @PostMapping("/reject/{requestId}")
    public MemberTransferRequest rejectRequest(
            @PathVariable String requestId,
            @RequestBody java.util.Map<String, String> body
    ) {
        String reason = body.get("decisionReason");
        return memberTransferService.rejectRequest(requestId, reason);
    }
}
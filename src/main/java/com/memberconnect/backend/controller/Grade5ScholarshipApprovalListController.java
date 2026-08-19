package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.Grade5ScholarshipApprovalListDTO;
import com.memberconnect.backend.model.Grade5ScholarshipRequest;
import com.memberconnect.backend.service.Grade5ScholarshipApprovalListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Head Office / Board Secretary territory (MMS07-MMS19).
 *
 * Annotations sit on the controller rather than the service so that a denial is not
 * swallowed by the catch(RuntimeException) inside each method and downgraded from 403
 * to 400 — AccessDeniedException is itself a RuntimeException.
 */
@RestController
@RequestMapping("/api/grade5/approval-lists")
@CrossOrigin(origins = "http://localhost:3000")
public class Grade5ScholarshipApprovalListController {

    @Autowired
    private Grade5ScholarshipApprovalListService service;

    @PreAuthorize("hasAuthority('G5_LIST_CREATE')")
    @PostMapping("/create")
    public ResponseEntity<?> createApprovalList(@RequestBody Grade5ScholarshipApprovalListDTO dto) {
        try {
            Grade5ScholarshipApprovalListDTO created = service.createApprovalList(dto);
            return ResponseEntity.ok(created);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('G5_LIST_VIEW')")
    @GetMapping("/all")
    public ResponseEntity<List<Grade5ScholarshipApprovalListDTO>> getAllApprovalLists() {
        return ResponseEntity.ok(service.getAllApprovalLists());
    }

    @PreAuthorize("hasAuthority('G5_LIST_VIEW')")
    @GetMapping("/{listId}")
    public ResponseEntity<?> getApprovalListByListId(@PathVariable String listId) {
        try {
            return ResponseEntity.ok(service.getApprovalListByListId(listId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('G5_LIST_VIEW')")
    @GetMapping("/{listId}/requests")
    public ResponseEntity<?> getRequestsByListId(@PathVariable String listId) {
        try {
            List<Grade5ScholarshipRequest> requests = service.getRequestsByListId(listId);
            return ResponseEntity.ok(requests);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('G5_LIST_DELETE')")
    @DeleteMapping("/{listId}")
    public ResponseEntity<?> deleteApprovalList(@PathVariable String listId) {
        try {
            String message = service.deleteApprovalList(listId);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('G5_LIST_PROCESS')")
    @PutMapping("/{listId}/process")
    public ResponseEntity<?> processApprovalList(
            @PathVariable String listId,
            @RequestBody Grade5ScholarshipApprovalListDTO dto
    ) {
        try {
            Grade5ScholarshipApprovalListDTO processed = service.processApprovalList(listId, dto);
            return ResponseEntity.ok(processed);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Repairs a list's own type/status metadata. Grouped with delete rights because it
    // rewrites list bookkeeping rather than acting on any individual request.
    @PreAuthorize("hasAuthority('G5_LIST_DELETE')")
    @PostMapping("/{listId}/restore")
    public ResponseEntity<?> restoreApprovalList(@PathVariable String listId) {
        try {
            Grade5ScholarshipApprovalListDTO restored = service.restoreApprovalList(listId);
            return ResponseEntity.ok(restored);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}

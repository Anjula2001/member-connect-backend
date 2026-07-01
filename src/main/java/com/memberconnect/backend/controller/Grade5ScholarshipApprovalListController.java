package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.Grade5ScholarshipApprovalListDTO;
import com.memberconnect.backend.model.Grade5ScholarshipRequest;
import com.memberconnect.backend.service.Grade5ScholarshipApprovalListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/grade5/approval-lists")
@CrossOrigin(origins = "http://localhost:3000")
public class Grade5ScholarshipApprovalListController {

    @Autowired
    private Grade5ScholarshipApprovalListService service;

    @PostMapping("/create")
    public ResponseEntity<?> createApprovalList(@RequestBody Grade5ScholarshipApprovalListDTO dto) {
        try {
            Grade5ScholarshipApprovalListDTO created = service.createApprovalList(dto);
            return ResponseEntity.ok(created);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<Grade5ScholarshipApprovalListDTO>> getAllApprovalLists() {
        return ResponseEntity.ok(service.getAllApprovalLists());
    }

    @GetMapping("/{listId}")
    public ResponseEntity<?> getApprovalListByListId(@PathVariable String listId) {
        try {
            return ResponseEntity.ok(service.getApprovalListByListId(listId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{listId}/requests")
    public ResponseEntity<?> getRequestsByListId(@PathVariable String listId) {
        try {
            List<Grade5ScholarshipRequest> requests = service.getRequestsByListId(listId);
            return ResponseEntity.ok(requests);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{listId}")
    public ResponseEntity<?> deleteApprovalList(@PathVariable String listId) {
        try {
            String message = service.deleteApprovalList(listId);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

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
}

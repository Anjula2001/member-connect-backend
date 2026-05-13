package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.CreateMemberDeathDTO;
import com.memberconnect.backend.dto.MarkIncompleteDTO;
import com.memberconnect.backend.dto.MemberDeathResponseDTO;
import com.memberconnect.backend.service.MemberDeathService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/member-deaths")
@CrossOrigin 
public class MemberDeathController {

    @Autowired
    private MemberDeathService memberDeathService;

    // Create

    @PostMapping
    public ResponseEntity<?> createRecord(@Valid @RequestBody CreateMemberDeathDTO dto) {
        try {
            MemberDeathResponseDTO response = memberDeathService.createRecord(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // Save (update draft)

    @PutMapping("/{id}/save")
    public ResponseEntity<?> saveRecord(@PathVariable Long id,
                                        @Valid @RequestBody CreateMemberDeathDTO dto) {
        try {
            MemberDeathResponseDTO response = memberDeathService.saveRecord(id, dto);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // Submit

    @PutMapping("/{id}/submit")
    public ResponseEntity<?> submitRecord(@PathVariable Long id,
                                          @RequestBody(required = false) CreateMemberDeathDTO dto) {
        try {
            MemberDeathResponseDTO response = memberDeathService.submitRecord(id, dto);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // Mark incomplete

    @PutMapping("/{id}/incomplete")
    public ResponseEntity<?> markIncomplete(@PathVariable Long id,
                                            @Valid @RequestBody MarkIncompleteDTO dto) {
        try {
            MemberDeathResponseDTO response = memberDeathService.markIncomplete(id, dto);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // Get record by ID

    @GetMapping("/{id}")
    public ResponseEntity<?> getRecord(@PathVariable Long id) {
        try {
            MemberDeathResponseDTO response = memberDeathService.getRecord(id);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllRecords() {
        try {
            return ResponseEntity.ok(memberDeathService.getAllRecords());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}

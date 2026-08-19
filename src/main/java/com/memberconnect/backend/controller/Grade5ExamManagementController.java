package com.memberconnect.backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.Grade5ExamManagementDTO;
import com.memberconnect.backend.service.Grade5ExamManagementService;

@RestController
@RequestMapping("/api/grade5/exam-management")
@CrossOrigin(origins = "http://localhost:3000")
public class Grade5ExamManagementController {

    private final Grade5ExamManagementService service;

    public Grade5ExamManagementController(Grade5ExamManagementService service) {
        this.service = service;
    }

    // Read is open to anyone who works with requests — the cut-off marks are shown on
    // the request entry screen, so District Office needs them to key a request at all.
    @PreAuthorize("hasAuthority('G5_EXAM_MASTER_VIEW')")
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentExamDetails(@RequestParam(required = false) Integer year) {
        try {
            Grade5ExamManagementDTO details = service.getExamDetails(year);
            return ResponseEntity.ok(details);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Writing exam dates and cut-off marks changes the outcome of every eligibility
    // check in the module, so it is held far more narrowly than reading them.
    @PreAuthorize("hasAuthority('G5_EXAM_MASTER_MANAGE')")
    @PostMapping("/save")
    public ResponseEntity<?> saveExamDetails(@RequestBody Grade5ExamManagementDTO dto) {
        try {
            service.saveExamDetails(dto);
            return ResponseEntity.ok(Map.of("message", "Grade 5 Exam and district cutoffs saved successfully."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "An error occurred while saving: " + e.getMessage()));
        }
    }
}

package com.memberconnect.backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
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

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentExamDetails(@RequestParam(required = false) Integer year) {
        try {
            Grade5ExamManagementDTO details = service.getExamDetails(year);
            return ResponseEntity.ok(details);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

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

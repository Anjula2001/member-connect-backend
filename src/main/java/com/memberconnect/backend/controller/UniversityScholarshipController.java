package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.ProgramOptionDto;
import com.memberconnect.backend.dto.UniversityScholarshipRequestDto;
import com.memberconnect.backend.model.University;
import com.memberconnect.backend.model.UniversityScholarshipRequest;
import com.memberconnect.backend.service.UniversityScholarshipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class UniversityScholarshipController {

    private final UniversityScholarshipService service;

    public UniversityScholarshipController(UniversityScholarshipService service) {
        this.service = service;
    }

    @GetMapping("/validate-exam-no")
    public ResponseEntity<Map<String, Object>> validateExamNo(@RequestParam String ExamNumber) {
        boolean duplicate = service.isExamNoDuplicate(ExamNumber);

        return ResponseEntity.ok(
                Map.of(
                        "duplicate", duplicate,
                        "message", duplicate
                                ? "Entered Examination Number is duplicating with another Scholarship Request"
                                : "Examination Number is valid"
                )
        );
    }

    @GetMapping("/universities")
    public List<University> getUniversities() {
        return service.getAllUniversities();
    }

    @GetMapping("/programs/{universityId}")
    public List<ProgramOptionDto> getProgramsByUniversity(@PathVariable Long universityId) {
        return service.getProgramsByUniversity(universityId);
    }

    @GetMapping("/duration")
    public Integer getDuration(
            @RequestParam Long universityId,
            @RequestParam Long programId
    ) {
        return service.getDuration(universityId, programId);
    }

    @PostMapping("/university-scholarships")
    public ResponseEntity<?> createRequest(@RequestBody UniversityScholarshipRequestDto dto) {
        try {
            UniversityScholarshipRequest saved = service.saveRequest(dto);
            return ResponseEntity.ok(saved);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", ex.getMessage())
            );
        }
    }

    @PostMapping("/university-scholarships/submit/{id}")
    public ResponseEntity<?> submitRequest(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.submitRequest(id));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/minor-account/check")
    public ResponseEntity<Map<String, Object>> checkMinorAccount(
            @RequestParam String birthCertificateNumber
    ) {
        return ResponseEntity.ok(
                service.checkMinorAccount(birthCertificateNumber)
        );
    }

    @PostMapping("/university-scholarships/incomplete/{id}")
    public ResponseEntity<?> markIncomplete(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        try {
            String reason = body.get("reason");

            UniversityScholarshipRequest updated =
                    service.markAsIncomplete(id, reason);

            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/university-scholarships")
    public ResponseEntity<?> getAllRequests() {
        return ResponseEntity.ok(service.getAllScholarshipRequests());
    }

}
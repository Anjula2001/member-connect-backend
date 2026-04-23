package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.ProgramOptionDto;
import com.memberconnect.backend.dto.UniversityScholarshipRequestDto;
import com.memberconnect.backend.model.University;
import com.memberconnect.backend.model.UniversityScholarshipRequest;
import com.memberconnect.backend.service.UniversityScholarshipService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.memberconnect.backend.repository.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class UniversityScholarshipController {

    private final UniversityScholarshipService service;
    

    public UniversityScholarshipController(UniversityScholarshipService service, UniversityRepository universityRepository) {
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

   /*@PostMapping
    public ResponseEntity<?> createRequest(@RequestBody UniversityScholarshipRequest request) {
        try {
            UniversityScholarshipRequest saved = service.saveRequest(request);
            return ResponseEntity.ok(saved);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(
                Map.of("message", ex.getMessage())
            );
        }
    }*/

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

    @PostMapping
    public UniversityScholarshipRequest createRequest(
            @RequestBody UniversityScholarshipRequestDto dto
    ) {
        return service.saveRequest(dto);
    }
}

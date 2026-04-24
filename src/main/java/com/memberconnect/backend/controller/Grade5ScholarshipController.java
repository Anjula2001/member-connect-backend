package com.memberconnect.backend.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.Grade5StudentDTO;
import com.memberconnect.backend.model.Grade5ScholarshipRequest;
import com.memberconnect.backend.service.Grade5ScholarshipService;

@RestController
@RequestMapping("/api/grade5")
@CrossOrigin(origins = "http://localhost:3000")
public class Grade5ScholarshipController {

    @Autowired
    private Grade5ScholarshipService service;

    // ✅ Validate exam number
    @GetMapping("/exists")
    public Map<String, Boolean> checkExamNumber(
            @RequestParam String examNo
    ) {
        boolean exists = service.isExamNumberExists(examNo);

        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);

        return response;
    }

    // ✅ Save full request
    @PostMapping("/save")
    public ResponseEntity<?> saveRequest(@RequestBody Grade5StudentDTO dto) {
        try {
            Grade5ScholarshipRequest saved = service.saveRequest(dto);
            return ResponseEntity.ok(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                .body("Entered Examination Number is duplicating with another Scholarship Request");
        }
}
}
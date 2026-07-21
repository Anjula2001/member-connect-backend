package com.memberconnect.backend.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.Grade5StudentDTO;
import com.memberconnect.backend.model.Grade5ScholarshipRequest;
import com.memberconnect.backend.model.MinorSavingsAccount;
import com.memberconnect.backend.service.Grade5ScholarshipService;

@RestController
@RequestMapping("/api/grade5")
@CrossOrigin(origins = "http://localhost:3000")
public class Grade5ScholarshipController {

    @Autowired
    private Grade5ScholarshipService service;

    // Validate exam number
    @GetMapping("/exists")
    public Map<String, Boolean> checkExamNumber(
            @RequestParam String examNo
    ) {
        boolean exists = service.isExamNumberExists(examNo);

        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);

        return response;
    }

    // Save full request
    @PostMapping("/save")
    public ResponseEntity<?> saveRequest(
            @RequestParam String memberId,
            @RequestBody Grade5StudentDTO dto
    ) {
        try {
            Grade5ScholarshipRequest saved = service.saveRequest(memberId, dto);
            return ResponseEntity.ok(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    // Get minor account details
    @GetMapping("/minor-account")
    public List<MinorSavingsAccount> getMinorAccount(
            @RequestParam String birthCertificateNo
    ) {
        return service.getMinorAccounts(birthCertificateNo);
    }

    // Get fund disbursement details
    @GetMapping("/fund-details")
    public Map<String, Object> getFundDetails(
            @RequestParam String birthCertificateNo
    ) {
        return service.getFundDisbursementDetails(birthCertificateNo);
    }

    // Get latest request for member
    @GetMapping("/{memberId}/request")
    public ResponseEntity<?> getLatestRequest(
            @PathVariable String memberId
    ) {
        Grade5ScholarshipRequest request = service.getLatestRequest(memberId);

        if (request == null) {
            return ResponseEntity.ok().body(null); // send JSON null
        }

        return ResponseEntity.ok(request);
    }

    // Get a specific request by requestNo
    @GetMapping("/request/{requestNo}")
    public ResponseEntity<?> getRequestByRequestNo(
            @PathVariable String requestNo
    ) {
        return service.getRequestByRequestNo(requestNo)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Mark incomplete
    @PutMapping("/{requestNo}/mark-incomplete")
    public Grade5ScholarshipRequest markIncomplete(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return service.markIncomplete(requestNo, reason);
    }
    
    // Submit request
    @PutMapping("/{requestNo}/submit")
    public ResponseEntity<?> submitRequest(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        try {
            String status = body.get("status");

            Grade5ScholarshipRequest updated =
                    service.submitRequest(requestNo, status);

            return ResponseEntity.ok(updated);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Get exam years for dropdown
    @GetMapping("/exam-years")
    public List<Integer> getExamYears() {
        return service.getExamYears();
    }

    // Check deviation info for a requested date and exam year
    @GetMapping("/check-deviation")
    public ResponseEntity<?> checkDeviation(
            @RequestParam String requestedDate,
            @RequestParam Integer examYear
    ) {
        try {
            java.time.LocalDate reqDate = java.time.LocalDate.parse(requestedDate);
            Map<String, Object> info = service.computeDeviationInfo(reqDate, examYear);
            return ResponseEntity.ok(info);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    //get all created scholarship requests
    @GetMapping("/requests/search")
    public ResponseEntity<?> searchRequests(
            @RequestParam(required = false) List<String> years,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(defaultValue = "ALL_DAYS") String receivedOn,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "REQUESTED_DATE") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        try {
            return ResponseEntity.ok(
                    service.searchRequests(years,statuses,receivedOn,fromDate,toDate,search,sortBy,sortDirection)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Update request details
    @PutMapping("/{requestNo}/update")
    public ResponseEntity<?> updateRequest(
            @PathVariable String requestNo,
            @RequestBody Grade5StudentDTO dto
    ) {
        try {
            Grade5ScholarshipRequest updated = service.updateRequest(requestNo, dto);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Change request status (view mode)
    @PutMapping("/{requestNo}/status")
    public ResponseEntity<?> changeRequestStatus(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        try {
            String status = body.get("status");
            Grade5ScholarshipRequest updated = service.changeRequestStatus(requestNo, status);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
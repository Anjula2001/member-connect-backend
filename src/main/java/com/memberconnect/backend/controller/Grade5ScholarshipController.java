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

import org.springframework.security.access.prepost.PreAuthorize;

import com.memberconnect.backend.config.CurrentUserService;
import com.memberconnect.backend.dto.Grade5StudentDTO;
import com.memberconnect.backend.enums.Permission;
import com.memberconnect.backend.enums.ScholarshipRequestStatus;
import com.memberconnect.backend.model.Grade5ScholarshipRequest;
import com.memberconnect.backend.model.MinorSavingsAccount;
import com.memberconnect.backend.service.Grade5ScholarshipService;

/**
 * Authorization note: the @PreAuthorize annotations sit on the controller rather than
 * the service on purpose. Every method here wraps its service call in
 * catch(RuntimeException) and AccessDeniedException extends RuntimeException, so a
 * denial raised inside the service would be caught and downgraded to 400 Bad Request.
 * Annotating the controller means Spring rejects the call before the method body runs
 * and GlobalExceptionHandler returns a proper 403.
 */
@RestController
@RequestMapping("/api/grade5")
@CrossOrigin(origins = "http://localhost:3000")
public class Grade5ScholarshipController {

    @Autowired
    private Grade5ScholarshipService service;

    @Autowired
    private CurrentUserService currentUserService;

    // Validate exam number
    @PreAuthorize("hasAuthority('G5_REQUEST_CREATE') or hasAuthority('G5_REQUEST_EDIT')")
    @GetMapping("/exists")
    public Map<String, Boolean> checkExamNumber(
            @RequestParam String examNo
    ) {
        boolean exists = service.isExamNumberExists(examNo);

        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);

        return response;
    }

    // Validate birth certificate number
    @PreAuthorize("hasAuthority('G5_REQUEST_CREATE') or hasAuthority('G5_REQUEST_EDIT')")
    @GetMapping("/exists-birth-certificate")
    public Map<String, Boolean> checkBirthCertificateNumber(
            @RequestParam String birthCertificateNo
    ) {
        boolean exists = service.isBirthCertificateNumberExists(birthCertificateNo);

        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);

        return response;
    }

    // Save full request
    @PreAuthorize("hasAuthority('G5_REQUEST_CREATE')")
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
    @PreAuthorize("hasAuthority('G5_REQUEST_CREATE') or hasAuthority('G5_REQUEST_EDIT')")
    @GetMapping("/minor-account")
    public List<MinorSavingsAccount> getMinorAccount(
            @RequestParam String birthCertificateNo
    ) {
        return service.getMinorAccounts(birthCertificateNo);
    }

    // Get fund disbursement details
    @PreAuthorize("hasAuthority('G5_REQUEST_CREATE') or hasAuthority('G5_REQUEST_EDIT')")
    @GetMapping("/fund-details")
    public Map<String, Object> getFundDetails(
            @RequestParam String birthCertificateNo,
            @RequestParam(required = false) Integer examYear
    ) {
        return service.getFundDisbursementDetails(birthCertificateNo, examYear);
    }

    // Calculate fund disbursement breakdown
    @PreAuthorize("hasAuthority('G5_REQUEST_CREATE') or hasAuthority('G5_REQUEST_EDIT')")
    @GetMapping("/calculate-disbursement")
    public Map<String, Object> calculateDisbursement(
            @RequestParam(defaultValue = "0") Integer months,
            @RequestParam(required = false) String option,
            @RequestParam(defaultValue = "false") Boolean hasMinorAccount
    ) {
        return service.calculateDisbursement(months, option, hasMinorAccount);
    }

    // Get latest request for member
    @PreAuthorize("hasAuthority('G5_REQUEST_VIEW')")
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

    // All requests for a member - a member can hold one per child, and one per exam year.
    @PreAuthorize("hasAuthority('G5_REQUEST_VIEW')")
    @GetMapping("/{memberId}/requests")
    public List<Grade5ScholarshipRequest> getRequestsForMember(
            @PathVariable String memberId
    ) {
        return service.getRequestsForMember(memberId);
    }

    // Get a specific request by requestNo
    @PreAuthorize("hasAuthority('G5_REQUEST_VIEW')")
    @GetMapping("/request/{requestNo}")
    public ResponseEntity<?> getRequestByRequestNo(
            @PathVariable String requestNo
    ) {
        return service.getRequestByRequestNo(requestNo)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Mark incomplete
    @PreAuthorize("hasAuthority('G5_REQUEST_INCOMPLETE')")
    @PutMapping("/{requestNo}/mark-incomplete")
    public Grade5ScholarshipRequest markIncomplete(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return service.markIncomplete(requestNo, reason);
    }

    // Submit request
    @PreAuthorize("hasAuthority('G5_REQUEST_SUBMIT')")
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
    @PreAuthorize("hasAuthority('G5_REQUEST_VIEW') or hasAuthority('G5_EXAM_MASTER_VIEW')")
    @GetMapping("/exam-years")
    public List<Integer> getExamYears() {
        return service.getExamYears();
    }

    // Check deviation info for a requested date and exam year
    @PreAuthorize("hasAuthority('G5_REQUEST_CREATE') or hasAuthority('G5_REQUEST_EDIT')")
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
    @PreAuthorize("hasAuthority('G5_REQUEST_VIEW')")
    @GetMapping("/requests/search")
    public ResponseEntity<?> searchRequests(
            @RequestParam(required = false) List<String> locations,
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
                    service.searchRequests(locations,years,statuses,receivedOn,fromDate,toDate,search,sortBy,sortDirection)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Update request details
    @PreAuthorize("hasAuthority('G5_REQUEST_EDIT')")
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

    // Send request to Finance Module
    @PreAuthorize("hasAuthority('G5_FINANCE_DISBURSE')")
    @PostMapping("/{requestNo}/send-to-finance")
    public ResponseEntity<?> sendToFinanceModule(@PathVariable String requestNo) {
        try {
            return ResponseEntity.ok(service.sendToFinanceModule(requestNo));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

   // Change request status 
    @PutMapping("/{requestNo}/status")
    public ResponseEntity<?> changeRequestStatus(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        String currentStatus = service.getRequestByRequestNo(requestNo)
                .map(Grade5ScholarshipRequest::getStatus)
                .orElse(null);
        currentUserService.require(
                requiredPermissionForStatusChange(currentStatus, body.get("status")));

        try {
            String status = body.get("status");
            Grade5ScholarshipRequest updated = service.changeRequestStatus(requestNo, status);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Determine the required permission for changing the status of a Grade 5 Scholarship request
    private Permission requiredPermissionForStatusChange(String currentStatus, String requestedStatus) {
        if (requestedStatus == null) {
            return Permission.G5_REQUEST_EDIT;
        }

        if (ScholarshipRequestStatus.INACTIVE.name().equalsIgnoreCase(requestedStatus)) {
            return Permission.G5_REQUEST_SET_INACTIVE;
        }

        boolean isReopeningRejection =
                ScholarshipRequestStatus.NEW.name().equalsIgnoreCase(requestedStatus)
                        && ScholarshipRequestStatus.REJECTED.name().equalsIgnoreCase(currentStatus);
        if (isReopeningRejection) {
            return Permission.G5_REQUEST_REOPEN;
        }

        return Permission.G5_REQUEST_EDIT;
    }
}

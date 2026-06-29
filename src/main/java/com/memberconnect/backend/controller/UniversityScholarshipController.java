package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.ProgramOptionDto;
import com.memberconnect.backend.dto.UniversityScholarshipFundRequestDto;
import com.memberconnect.backend.dto.UniversityScholarshipRequestDto;
import com.memberconnect.backend.enums.UniversityScholarshipRequestStatus;
import com.memberconnect.backend.model.University;
import com.memberconnect.backend.model.UniversityScholarshipRequest;
import com.memberconnect.backend.repository.UniversityScholarshipRequestRepository;
import com.memberconnect.backend.service.UniversityScholarshipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class UniversityScholarshipController {

    @Autowired
    private UniversityScholarshipRequestRepository universityScholarshipRepository;

    private final UniversityScholarshipService service;

    public UniversityScholarshipController(UniversityScholarshipService service) {
        this.service = service;
    }

    // Endpoint to validate examination number
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

    //Endpoint to get universities List 
    @GetMapping("/universities")
    public List<University> getUniversities() {
        return service.getAllUniversities();
    }

    // Endpoint to get programs by university ID
    @GetMapping("/programs/{universityId}")
    public List<ProgramOptionDto> getProgramsByUniversity(@PathVariable Long universityId) {
        return service.getProgramsByUniversity(universityId);
    }

    // Endpoint to get scholarship duration by university ID and program ID
    @GetMapping("/duration")
    public Integer getDuration(
            @RequestParam Long universityId,
            @RequestParam Long programId
    ) {
        return service.getDuration(universityId, programId);
    }

    // Endpoint to create a new scholarship request
    @PostMapping("/university-scholarships")
    public ResponseEntity<?> createRequest(@RequestBody UniversityScholarshipRequestDto dto) {
        System.out.println("=== CREATE REQUEST DTO VALUES ===");
        System.out.println("hasMinorAccount: " + dto.getHasMinorAccount());
        System.out.println("minorAccountMonths: " + dto.getMinorAccountMonths());
        try {
            UniversityScholarshipRequest saved = service.saveRequest(dto);
            return ResponseEntity.ok(saved);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", ex.getMessage())
            );
        }
    }

    // Endpoint to update an existing scholarship request by request ID
    @PutMapping("/university-scholarships/{requestId}")
    public ResponseEntity<?> updateRequest(
            @PathVariable String requestId,
            @RequestBody UniversityScholarshipRequestDto dto
    ) {
        System.out.println("=== UPDATE REQUEST DTO VALUES ===");
        System.out.println("hasMinorAccount: " + dto.getHasMinorAccount());
        System.out.println("minorAccountMonths: " + dto.getMinorAccountMonths());
        try {
            UniversityScholarshipRequest updated = service.updateRequestByRequestId(requestId, dto);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // Endpoint to update the approved-only editable scholarship details
    @PutMapping("/university-scholarships/{requestId}/approved-details")
    public ResponseEntity<?> updateApprovedDetails(
            @PathVariable String requestId,
            @RequestBody UniversityScholarshipRequestDto dto
    ) {
        try {
            service.updateApprovedDetailsByRequestId(requestId, dto);
            return ResponseEntity.ok(service.getScholarshipRequestByRequestId(requestId));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // Endpoint to submit a scholarship request by request ID
    @PostMapping("/university-scholarships/submit/{requestId}")
    public ResponseEntity<?> submitRequest(@PathVariable String requestId) {
        try {
            return ResponseEntity.ok(service.submitRequest(requestId));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // Endpoint to check minor account by birth certificate number
    @GetMapping("/minor-account/check")
    public ResponseEntity<Map<String, Object>> checkMinorAccount(
            @RequestParam String birthCertificateNumber
    ) {
        return ResponseEntity.ok(
                service.checkMinorAccount(birthCertificateNumber)
        );
    }

    // Endpoint to mark a scholarship request as incomplete with a reason
    @PostMapping("/university-scholarships/incomplete/{requestId}")
    public ResponseEntity<?> markIncomplete(
            @PathVariable String requestId,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        UniversityScholarshipRequest updated =
                service.markAsIncomplete(requestId, reason);

        return ResponseEntity.ok(updated);
    }

    // Endpoint to approve a scholarship request by request ID
    @PostMapping("/university-scholarships/approve/{requestId}")
    public ResponseEntity<?> approveRequest(@PathVariable String requestId) {
        try {
            UniversityScholarshipRequest updated = service.approveRequest(requestId);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // Endpoint to get all scholarship requests
    @GetMapping("/university-scholarships")
    public ResponseEntity<?> getAllRequests() {
        return ResponseEntity.ok(service.getAllScholarshipRequests());
    }

    // Endpoint to get all university scholarship requests for a member
    @GetMapping("/university-scholarships/member/{memberId}")
    public ResponseEntity<?> getRequestsByMember(@PathVariable String memberId) {
        return ResponseEntity.ok(service.getScholarshipRequestsByMemberId(memberId));
    }

    // Endpoint to get a scholarship request by request ID
    @GetMapping("/university-scholarships/{requestId}")
    public ResponseEntity<?> getRequestByRequestId(@PathVariable String requestId) {
        try {
            return ResponseEntity.ok(service.getScholarshipRequestByRequestId(requestId));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // Endpoint to save or update a University Scholarship Fund Request
    @PostMapping("/university-scholarships/{requestId}/fund-requests")
    public ResponseEntity<?> saveFundRequest(
            @PathVariable String requestId,
            @RequestBody UniversityScholarshipFundRequestDto dto
    ) {
        try {
            return ResponseEntity.ok(service.saveFundRequest(requestId, dto));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // Endpoint to submit a University Scholarship Fund Request
    @PostMapping("/university-scholarships/{requestId}/fund-requests/{fundRequestId}/submit")
    public ResponseEntity<?> submitFundRequest(
            @PathVariable String requestId,
            @PathVariable String fundRequestId
    ) {
        try {
            return ResponseEntity.ok(service.submitFundRequest(requestId, fundRequestId));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // Endpoint to reject a scholarship request by request ID with a reason
    @PostMapping("/university-scholarships/reject/{requestId}")
    public ResponseEntity<UniversityScholarshipRequest> rejectScholarship(
            @PathVariable String requestId,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("decisionReason"); 

        UniversityScholarshipRequest request =
                universityScholarshipRepository.findByUniversityScholarshipRequestID(requestId)
                        .orElseThrow(() -> new RuntimeException("Scholarship request not found"));

        request.setStatus(UniversityScholarshipRequestStatus.REJECTED);
        request.setRejectReason(reason); 

        UniversityScholarshipRequest saved =
                universityScholarshipRepository.save(request);

        return ResponseEntity.ok(saved);
    }

    // Endpoint to attach scholarship requests to a normal board meeting
    @PostMapping("/university-scholarships/attach-board-meeting")
    public ResponseEntity<?> attachBoardMeeting(@RequestBody Map<String, Object> payload) {
        try {
            service.attachBoardMeeting(payload);
            return ResponseEntity.ok(Map.of("message", "University Scholarship Requests successfully attached to Board Meeting"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Endpoint to attach scholarship requests to a deviation board meeting
    @PostMapping("/university-scholarships/attach-deviation-board-meeting")
    public ResponseEntity<?> attachDeviationBoardMeeting(@RequestBody Map<String, Object> payload) {
        try {
            service.attachDeviationBoardMeeting(payload);
            return ResponseEntity.ok(Map.of("message", "University Scholarship Requests successfully attached to Deviation Board Meeting"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Endpoint to delete a Normal Approval List and roll back attached requests
    @DeleteMapping("/university-scholarships/approval-list/{boardMeetingId}")
    public ResponseEntity<?> deleteApprovalList(@PathVariable Long boardMeetingId) {
        try {
            service.deleteApprovalList(boardMeetingId);
            return ResponseEntity.ok(Map.of("message", "Approval list deleted and requests rolled back successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Endpoint to delete a Deviation Approval List and roll back attached requests
    @DeleteMapping("/university-scholarships/deviation-approval-list/{boardMeetingId}")
    public ResponseEntity<?> deleteDeviationApprovalList(@PathVariable Long boardMeetingId) {
        try {
            service.deleteDeviationApprovalList(boardMeetingId);
            return ResponseEntity.ok(Map.of("message", "Deviation approval list deleted and requests rolled back successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}

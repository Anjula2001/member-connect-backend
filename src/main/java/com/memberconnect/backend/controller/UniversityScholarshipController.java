package com.memberconnect.backend.controller;

import com.memberconnect.backend.config.CurrentUserService;
import com.memberconnect.backend.dto.ProgramOptionDto;
import com.memberconnect.backend.enums.Permission;
import com.memberconnect.backend.dto.UniversityScholarshipFundRequestDto;
import com.memberconnect.backend.dto.UniversityScholarshipRequestDto;
import com.memberconnect.backend.enums.UniversityScholarshipFundRequestStatus;
import com.memberconnect.backend.enums.UniversityScholarshipRequestStatus;
import com.memberconnect.backend.model.University;
import com.memberconnect.backend.model.UniversityScholarshipRequest;
import com.memberconnect.backend.repository.UniversityScholarshipRequestRepository;
import com.memberconnect.backend.service.UniversityScholarshipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class UniversityScholarshipController {

    @Autowired
    private UniversityScholarshipRequestRepository universityScholarshipRepository;

    private final UniversityScholarshipService service;
    private final CurrentUserService currentUserService;

    public UniversityScholarshipController(UniversityScholarshipService service,
                                           CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    /**
     * MMS25 — change a request's status from View Mode.
     *
     * The right required depends on the target status, not on the endpoint, so this
     * cannot be a single @PreAuthorize. The check runs BEFORE the try/catch below:
     * inside it, AccessDeniedException is a RuntimeException and would be reported as
     * 400 instead of reaching the handler that turns it into 403.
     */
    @PutMapping("/university-scholarships/{requestId}/status")
    public ResponseEntity<?> changeRequestStatus(
            @PathVariable String requestId,
            @RequestBody Map<String, String> body
    ) {
        currentUserService.require(requiredPermissionForStatusChange(body.get("status")));

        try {
            return ResponseEntity.ok(service.changeRequestStatus(requestId, body.get("status")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Maps a status change to the right needed to perform it.
     *
     * Deliberately stricter than the Grade 5 equivalent, which lets INCOMPLETE -> NEW
     * and SUBMITTED -> NEW ride on ordinary G5_REQUEST_EDIT. Doing that here would
     * invert the intended actors: HEAD_OFFICE does not hold US_REQUEST_EDIT while
     * DISTRICT_OFFICE does, so the office meant to own this screen would be locked out
     * of it and the office meant to be excluded would get in. Every move back to NEW
     * therefore requires US_REQUEST_REOPEN, which sits with Super Admin, Head Office
     * and Board Secretary.
     *
     * An unrecognised status falls through to US_REQUEST_SET_INACTIVE — the narrower of
     * the two rights — and is then rejected by the service's own validation, so a bad
     * payload can never pick the weaker check.
     */
    private Permission requiredPermissionForStatusChange(String requestedStatus) {
        if (UniversityScholarshipRequestStatus.NEW.name().equalsIgnoreCase(requestedStatus)) {
            return Permission.US_REQUEST_REOPEN;
        }
        return Permission.US_REQUEST_SET_INACTIVE;
    }

    // Endpoint to validate examination number
    @PreAuthorize("hasAuthority('US_REQUEST_CREATE') or hasAuthority('US_REQUEST_EDIT')")
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
    @PreAuthorize("hasAuthority('US_MASTER_VIEW')")
    @GetMapping("/universities")
    public List<University> getUniversities() {
        return service.getAllUniversities();
    }

    // Endpoint to get programs by university ID
    @PreAuthorize("hasAuthority('US_MASTER_VIEW')")
    @GetMapping("/programs/{universityId}")
    public List<ProgramOptionDto> getProgramsByUniversity(@PathVariable Long universityId) {
        return service.getProgramsByUniversity(universityId);
    }

    // Endpoint to get scholarship duration by university ID and program ID
    @PreAuthorize("hasAuthority('US_MASTER_VIEW')")
    @GetMapping("/duration")
    public Integer getDuration(
            @RequestParam Long universityId,
            @RequestParam Long programId
    ) {
        return service.getDuration(universityId, programId);
    }

    // Endpoint to create a new scholarship request
    @PreAuthorize("hasAuthority('US_REQUEST_CREATE')")
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
    @PreAuthorize("hasAuthority('US_REQUEST_EDIT')")
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
    @PreAuthorize("hasAuthority('US_APPROVED_EDIT')")
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
    @PreAuthorize("hasAuthority('US_REQUEST_SUBMIT')")
    @PostMapping("/university-scholarships/submit/{requestId}")
    public ResponseEntity<?> submitRequest(@PathVariable String requestId) {
        try {
            return ResponseEntity.ok(service.submitRequest(requestId));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // Endpoint to check minor account by birth certificate number
    @PreAuthorize("hasAuthority('US_REQUEST_CREATE') or hasAuthority('US_REQUEST_EDIT')")
    @GetMapping("/minor-account/check")
    public ResponseEntity<Map<String, Object>> checkMinorAccount(
            @RequestParam String birthCertificateNumber
    ) {
        return ResponseEntity.ok(
                service.checkMinorAccount(birthCertificateNumber)
        );
    }

    // Endpoint to mark a scholarship request as incomplete with a reason
    @PreAuthorize("hasAuthority('US_REQUEST_INCOMPLETE')")
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
    /**
     * MMS26 — Committee approval.
     *
     * Split out from the old /approve endpoint, which also performed final Board
     * approval. Those are two different authority levels held by two different roles,
     * so a single @PreAuthorize could not express them; keeping them on one endpoint
     * also left a route that skipped the Board Meeting altogether.
     */
    @PreAuthorize("hasAuthority('US_COMMITTEE_APPROVE')")
    @PostMapping("/university-scholarships/committee-approve/{requestId}")
    public ResponseEntity<?> committeeApproveRequest(@PathVariable String requestId) {
        try {
            UniversityScholarshipRequest updated = service.committeeApproveRequest(requestId);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // Endpoint to get all scholarship requests
    @PreAuthorize("hasAuthority('US_REQUEST_VIEW')")
    @GetMapping("/university-scholarships")
    public ResponseEntity<?> getAllRequests() {
        return ResponseEntity.ok(service.getAllScholarshipRequests());
    }

    // Endpoint to get all university scholarship requests for a member
    @PreAuthorize("hasAuthority('US_REQUEST_VIEW')")
    @GetMapping("/university-scholarships/member/{memberId}")
    public ResponseEntity<?> getRequestsByMember(@PathVariable String memberId) {
        return ResponseEntity.ok(service.getScholarshipRequestsByMemberId(memberId));
    }

    // Endpoint to get a scholarship request by request ID
    @PreAuthorize("hasAuthority('US_REQUEST_VIEW')")
    @GetMapping("/university-scholarships/{requestId}")
    public ResponseEntity<?> getRequestByRequestId(@PathVariable String requestId) {
        try {
            return ResponseEntity.ok(service.getScholarshipRequestByRequestId(requestId));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // Endpoint to save or update a University Scholarship Fund Request
    @PreAuthorize("hasAuthority('US_FUND_CREATE') or hasAuthority('US_FUND_EDIT')")
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
    @PreAuthorize("hasAuthority('US_FUND_SUBMIT')")
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

    // Endpoint to mark a University Scholarship Fund Request as incomplete
    @PreAuthorize("hasAuthority('US_FUND_INCOMPLETE')")
    @PostMapping("/university-scholarships/{requestId}/fund-requests/{fundRequestId}/incomplete")
    public ResponseEntity<?> markFundRequestIncomplete(
            @PathVariable String requestId,
            @PathVariable String fundRequestId,
            @RequestBody Map<String, String> body
    ) {
        try {
            return ResponseEntity.ok(service.markFundRequestIncomplete(
                    requestId,
                    fundRequestId,
                    body.get("reason")
            ));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * MMS45 — change a fund request's status from View Mode (New / Inactive).
     *
     * PUT here, while PATCH on the same path carries the MMS47 approve/reject decision.
     * They are separate endpoints because they need different rights, and the right
     * this one needs depends on the target status — so, as with the scholarship request
     * equivalent, the check runs before the try/catch rather than as a @PreAuthorize.
     */
    @PutMapping("/university-scholarships/{requestId}/fund-requests/{fundRequestId}/status")
    public ResponseEntity<?> changeFundRequestStatus(
            @PathVariable String requestId,
            @PathVariable String fundRequestId,
            @RequestBody Map<String, String> body
    ) {
        currentUserService.require(requiredPermissionForFundStatusChange(body.get("status")));

        try {
            return ResponseEntity.ok(
                    service.changeFundRequestStatus(requestId, fundRequestId, body.get("status")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Maps a fund request status change to the right it needs. An unrecognised status
     * falls through to US_FUND_SET_INACTIVE and is then rejected by the service's own
     * validation, so a bad payload cannot pick a weaker check.
     */
    private Permission requiredPermissionForFundStatusChange(String requestedStatus) {
        if (UniversityScholarshipFundRequestStatus.NEW.name().equalsIgnoreCase(requestedStatus)) {
            return Permission.US_FUND_REOPEN;
        }
        return Permission.US_FUND_SET_INACTIVE;
    }

    /**
     * MMS48 — hand an approved fund request to the Finance Module.
     *
     * A single @PreAuthorize is enough here: unlike the status endpoints, the right
     * does not vary with the payload. There is no payload.
     */
    @PreAuthorize("hasAuthority('US_FINANCE_DISBURSE')")
    @PostMapping("/university-scholarships/{requestId}/fund-requests/{fundRequestId}/finance-integration")
    public ResponseEntity<?> integrateFundRequestWithFinance(
            @PathVariable String requestId,
            @PathVariable String fundRequestId
    ) {
        try {
            return ResponseEntity.ok(
                    service.integrateFundRequestWithFinance(requestId, fundRequestId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Endpoint to change allowed University Scholarship Fund Request statuses
    @PreAuthorize("hasAuthority('US_FUND_APPROVE')")
    @PatchMapping("/university-scholarships/{requestId}/fund-requests/{fundRequestId}/status")
    public ResponseEntity<?> updateFundRequestStatus(
            @PathVariable String requestId,
            @PathVariable String fundRequestId,
            @RequestBody Map<String, String> body
    ) {
        try {
            return ResponseEntity.ok(service.updateFundRequestStatus(
                    requestId,
                    fundRequestId,
                    body.get("status"),
                    body.getOrDefault("reason", body.get("incompleteReason"))
            ));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // Endpoint to reject a scholarship request by request ID with a reason
    /**
     * MMS26 — Committee rejection.
     *
     * The logic moved into the service: this used to write to the repository directly
     * from the controller with no status check, so any request in any state — including
     * one already Approved — could be flipped to Rejected.
     */
    @PreAuthorize("hasAuthority('US_COMMITTEE_APPROVE')")
    @PostMapping("/university-scholarships/committee-reject/{requestId}")
    public ResponseEntity<?> committeeRejectScholarship(
            @PathVariable String requestId,
            @RequestBody Map<String, String> body
    ) {
        try {
            UniversityScholarshipRequest saved =
                    service.committeeRejectRequest(requestId, body.get("decisionReason"));
            return ResponseEntity.ok(saved);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // Endpoint to attach scholarship requests to a normal board meeting
    @PreAuthorize("hasAuthority('US_LIST_CREATE')")
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
    @PreAuthorize("hasAuthority('US_LIST_CREATE')")
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
    @PreAuthorize("hasAuthority('US_LIST_DELETE')")
    @DeleteMapping("/university-scholarships/approval-list/{approvalListId}")
    public ResponseEntity<?> deleteApprovalList(@PathVariable String approvalListId) {
        try {
            service.deleteApprovalList(approvalListId);
            return ResponseEntity.ok(Map.of("message", "Approval list deleted and requests rolled back successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Endpoint to delete a Deviation Approval List and roll back attached requests
    @PreAuthorize("hasAuthority('US_LIST_DELETE')")
    @DeleteMapping("/university-scholarships/deviation-approval-list/{approvalListId}")
    public ResponseEntity<?> deleteDeviationApprovalList(@PathVariable String approvalListId) {
        try {
            service.deleteDeviationApprovalList(approvalListId);
            return ResponseEntity.ok(Map.of("message", "Deviation approval list deleted and requests rolled back successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('US_LIST_PROCESS')")
    @PostMapping(value = "/university-scholarships/process-approvals", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> processApprovals(
            @RequestParam("data") String dataJson,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        try {
            service.processApprovals(dataJson, file);
            return ResponseEntity.ok(Map.of("message", "Approvals processed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('US_LIST_PROCESS')")
    @PostMapping(value = "/university-scholarships/process-deviation-approvals", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> processDeviationApprovals(
            @RequestParam("data") String dataJson,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        try {
            service.processDeviationApprovals(dataJson, file);
            return ResponseEntity.ok(Map.of("message", "Deviation approvals processed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('US_LIST_VIEW')")
    @GetMapping("/university-scholarships/download-report/{approvalListId}")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String approvalListId) {
        try {
            List<UniversityScholarshipRequest> requests = universityScholarshipRepository.findByApprovalListId(approvalListId);
            String filePath = requests.stream()
                    .map(UniversityScholarshipRequest::getScannedReportPath)
                    .filter(path -> path != null && !path.isBlank())
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No report found for list: " + approvalListId));

            byte[] content = service.downloadFile(filePath);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Scanned_Report_" + approvalListId + "\"")
                    .body(content);
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).build();
        }
    }

}

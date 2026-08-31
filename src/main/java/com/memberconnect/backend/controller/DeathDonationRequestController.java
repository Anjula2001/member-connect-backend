package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.DeathDonationDeceasedPopulateDTO;
import com.memberconnect.backend.dto.DeathDonationDocumentDTO;
import com.memberconnect.backend.dto.DeathDonationRelativeDTO;
import com.memberconnect.backend.dto.DeathDonationRequestDTO;
import com.memberconnect.backend.model.DeathDonationDocument;
import com.memberconnect.backend.service.DeathDonationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Death Donations for Members (SRS Requirement 05, section 2, MMD01-MMD08).
 *
 * Roles here mirror the SRS actors. Entry and editing belong to the District
 * Office; the three decision levels each belong to their own role, which is what
 * stops one clerk walking a request from submission to approval on their own.
 * The service repeats every check at runtime (assertMayDecideAtCurrentLevel,
 * assertCallerMayAccess, assertNotSelfApproval), so these annotations are the
 * outer gate, not the only one.
 *
 * ACCOUNTS, SCHOLARSHIP_OFFICER and DEATH_DONATION_OFFICER appear nowhere below:
 * none of them is an actor in SRS section 2.
 */
@RestController
@RequestMapping("/api/death-donation-requests")
@CrossOrigin(origins = "http://localhost:3000")
public class DeathDonationRequestController {

    /** Anyone who takes part in the donation workflow may read it (MMD02 / MMD03). */
    private static final String READ_ROLES =
            "hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE',"
            + "'HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')";

    /** Only the District Office raises and edits requests (MMD01 / MMD04). */
    private static final String ENTRY_ROLES = "hasAnyRole('DISTRICT_OFFICE','SUPER_ADMIN')";

    /** The three decision levels (MMD05 / MMD06 / MMD07). */
    private static final String DECIDE_ROLES =
            "hasAnyRole('DISTRICT_OFFICE','DISTRICT_COMMITTEE','PD_COMMITTEE','SUPER_ADMIN')";

    /** MMD04 manual status changes, including the Inactive right. */
    private static final String STATUS_CHANGE_ROLES =
            "hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')";

    private final DeathDonationService deathDonationService;

    public DeathDonationRequestController(DeathDonationService deathDonationService) {
        this.deathDonationService = deathDonationService;
    }

    // ---------------- Reads (MMD02 / MMD03) ----------------

    /**
     * MMD02. {@code locations} is honoured for Head Office and committee users; a
     * District Office user is pinned to their own district regardless of what
     * they ask for.
     */
    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<DeathDonationRequestDTO> searchRequests(
            @RequestParam(required = false) List<String> locations,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String searchKey,
            @RequestParam(required = false, defaultValue = "requestedDate") String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortOrder
    ) {
        return deathDonationService.searchRequests(
                locations,
                statuses,
                fromDate,
                toDate,
                searchKey,
                sortBy,
                sortOrder
        );
    }

    @GetMapping("/relationships")
    @PreAuthorize(READ_ROLES)
    public List<String> getRelationshipOptions() {
        return deathDonationService.getRelationshipOptions();
    }

    @GetMapping("/member/{memberId}")
    @PreAuthorize(READ_ROLES)
    public List<DeathDonationRequestDTO> getRequestsByMember(@PathVariable String memberId) {
        return deathDonationService.getRequestsByMember(memberId);
    }

    @GetMapping("/{requestNo}")
    @PreAuthorize(READ_ROLES)
    public DeathDonationRequestDTO getRequest(@PathVariable String requestNo) {
        return deathDonationService.getRequestByRequestNo(requestNo);
    }

    // ---------------- Entry and editing (MMD01 / MMD04) ----------------

    @PostMapping("/{memberId}")
    @PreAuthorize(ENTRY_ROLES)
    public DeathDonationRequestDTO saveRequest(
            @PathVariable String memberId,
            @RequestBody DeathDonationRequestDTO dto
    ) {
        return deathDonationService.saveRequest(memberId, dto);
    }

    /**
     * Update. Deliberately carries DECIDE_ROLES rather than ENTRY_ROLES: once a
     * request is locked this path becomes the Concerns Identified update, which
     * the SRS opens to every approving level. The service decides which of the
     * two it is and applies the matching check.
     */
    @PutMapping("/{requestNo}")
    @PreAuthorize(DECIDE_ROLES)
    public DeathDonationRequestDTO updateRequest(
            @PathVariable String requestNo,
            @RequestBody DeathDonationRequestDTO dto
    ) {
        dto.setRequestNo(requestNo);
        DeathDonationRequestDTO existing = deathDonationService.getRequestByRequestNo(requestNo);
        return deathDonationService.saveRequest(existing.getMemberId(), dto);
    }

    @PostMapping("/{requestNo}/submit")
    @PreAuthorize(ENTRY_ROLES)
    public DeathDonationRequestDTO submitRequest(@PathVariable String requestNo) {
        return deathDonationService.submitRequest(requestNo);
    }

    @PutMapping("/{requestNo}/mark-incomplete")
    @PreAuthorize(ENTRY_ROLES)
    public DeathDonationRequestDTO markIncomplete(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        return deathDonationService.markIncomplete(requestNo, body.get("reason"));
    }

    /**
     * Manual status change within the MMD04 matrix only. Making a request
     * inactive additionally needs inactive rights, which the service checks.
     */
    @PutMapping("/{requestNo}/change-status")
    @PreAuthorize(STATUS_CHANGE_ROLES)
    public DeathDonationRequestDTO changeStatus(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        return deathDonationService.changeStatus(requestNo, body.get("status"));
    }

    /** The Concerns Identified field, editable in View Mode for approvers. */
    @PutMapping("/{requestNo}/concerns")
    @PreAuthorize(DECIDE_ROLES)
    public DeathDonationRequestDTO updateConcerns(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        return deathDonationService.updateConcerns(requestNo, body.get("concerns"));
    }

    // ---------------- Decisions (MMD05 / MMD06 / MMD07) ----------------

    /**
     * Approve at the level the request currently sits. The annotation admits
     * every decision role; the service then requires the caller to own the level
     * the request is actually at, so a District Committee user cannot approve a
     * request still waiting on the District Office.
     */
    @PutMapping("/{requestNo}/approve")
    @PreAuthorize(DECIDE_ROLES)
    public DeathDonationRequestDTO approveRequest(@PathVariable String requestNo) {
        return deathDonationService.approveRequest(requestNo);
    }

    @PutMapping("/{requestNo}/reject")
    @PreAuthorize(DECIDE_ROLES)
    public DeathDonationRequestDTO rejectRequest(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        return deathDonationService.rejectRequest(requestNo, body.get("reason"));
    }

    /** MMD05: escalate from the District Office to the District Committee. */
    @PutMapping("/{requestNo}/forward-to-district-committee")
    @PreAuthorize(ENTRY_ROLES)
    public DeathDonationRequestDTO forwardToDistrictCommittee(
            @PathVariable String requestNo,
            @RequestBody(required = false) Map<String, String> body
    ) {
        return deathDonationService.forwardToNextLevel(requestNo, concernsOf(body));
    }

    /** MMD06: escalate from the District Committee to the P&D Committee. */
    @PutMapping("/{requestNo}/forward-to-pd-committee")
    @PreAuthorize("hasAnyRole('DISTRICT_COMMITTEE','SUPER_ADMIN')")
    public DeathDonationRequestDTO forwardToPdCommittee(
            @PathVariable String requestNo,
            @RequestBody(required = false) Map<String, String> body
    ) {
        return deathDonationService.forwardToNextLevel(requestNo, concernsOf(body));
    }

    // ---------------- Death Donation Details (SRS 2.2.3) ----------------

    /**
     * The SRS refresh button: takes whatever the three operator-editable inputs
     * currently hold and recalculates the rest of the entitlement from them.
     */
    @PostMapping("/{requestNo}/donation/refresh")
    @PreAuthorize(DECIDE_ROLES)
    public DeathDonationRequestDTO refreshDonationEntitlement(
            @PathVariable String requestNo,
            @RequestBody(required = false) Map<String, String> body
    ) {
        return deathDonationService.refreshDonationEntitlement(
                requestNo,
                integerOf(body, "monthsRemitted"),
                decimalOf(body, "receivedPast12Months"),
                decimalOf(body, "creditedToSpecialFixedAccount")
        );
    }

    // ---------------- Close relatives grid and deceased lookup ----------------

    @GetMapping("/relatives-by-certificate")
    @PreAuthorize(READ_ROLES)
    public List<DeathDonationRelativeDTO> refreshRelatives(
            @RequestParam String deathCertificateNumber,
            @RequestParam(required = false) String excludeRequestNo
    ) {
        return deathDonationService.refreshRelatives(deathCertificateNumber, excludeRequestNo);
    }

    @GetMapping("/deceased-member/{memberId}/populate")
    @PreAuthorize(ENTRY_ROLES)
    public DeathDonationDeceasedPopulateDTO populateDeceasedMember(@PathVariable String memberId) {
        return deathDonationService.populateDeceasedMember(memberId);
    }

    // ---------------- Documents ----------------

    @GetMapping("/{requestNo}/required-documents")
    @PreAuthorize(READ_ROLES)
    public List<DeathDonationDocumentDTO> getRequiredDocuments(@PathVariable String requestNo) {
        return deathDonationService.getRequiredDocuments(requestNo);
    }

    @GetMapping("/{requestNo}/documents")
    @PreAuthorize(READ_ROLES)
    public List<DeathDonationDocumentDTO> getDocuments(@PathVariable String requestNo) {
        return deathDonationService.getDocuments(requestNo);
    }

    @PostMapping("/{requestNo}/documents/{documentType}/upload")
    @PreAuthorize(ENTRY_ROLES)
    public DeathDonationDocumentDTO uploadDocument(
            @PathVariable String requestNo,
            @PathVariable String documentType,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return deathDonationService.uploadDocument(requestNo, documentType, file);
    }

    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize(ENTRY_ROLES)
    public void deleteDocument(@PathVariable Long documentId) {
        deathDonationService.deleteDocument(documentId);
    }

    /**
     * Streams the file back.
     *
     * The browser is told the real content type and file name, which the previous
     * version omitted - it sent a bare "inline" disposition with no type, so a
     * PDF and a JPEG arrived indistinguishable and the viewer had to guess.
     */
    @GetMapping("/documents/{documentId}/download")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long documentId) {
        DeathDonationDocument document = deathDonationService.getDocumentEntity(documentId);
        byte[] fileBytes = deathDonationService.downloadDocument(documentId);

        String contentType = document.getMimeType() != null && !document.getMimeType().isBlank()
                ? document.getMimeType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        String fileName = document.getFileName() != null ? document.getFileName() : "document";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(fileBytes);
    }

    private String concernsOf(Map<String, String> body) {
        return body != null ? body.get("concerns") : null;
    }

    /** A missing or blank value means "leave it alone", not "set it to zero". */
    private Integer integerOf(Map<String, String> body, String key) {
        String raw = rawOf(body, key);
        return raw != null ? Integer.valueOf(raw) : null;
    }

    private BigDecimal decimalOf(Map<String, String> body, String key) {
        String raw = rawOf(body, key);
        return raw != null ? new BigDecimal(raw) : null;
    }

    private String rawOf(Map<String, String> body, String key) {
        if (body == null) {
            return null;
        }
        String value = body.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

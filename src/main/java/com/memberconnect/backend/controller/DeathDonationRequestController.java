package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.DeathDonationDeceasedPopulateDTO;
import com.memberconnect.backend.dto.DeathDonationDocumentDTO;
import com.memberconnect.backend.dto.DeathDonationRelativeDTO;
import com.memberconnect.backend.dto.DeathDonationRequestDTO;
import com.memberconnect.backend.service.DeathDonationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/death-donation-requests")
@CrossOrigin(origins = "http://localhost:3000")
public class DeathDonationRequestController {

    private final DeathDonationService deathDonationService;

    public DeathDonationRequestController(DeathDonationService deathDonationService) {
        this.deathDonationService = deathDonationService;
    }

    @GetMapping
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

    @GetMapping("/member/{memberId}")
    public List<DeathDonationRequestDTO> getRequestsByMember(@PathVariable String memberId) {
        return deathDonationService.getRequestsByMember(memberId);
    }

    @GetMapping("/{requestNo}")
    public DeathDonationRequestDTO getRequest(@PathVariable String requestNo) {
        return deathDonationService.getRequestByRequestNo(requestNo);
    }

    @PostMapping("/{memberId}")
    public DeathDonationRequestDTO saveRequest(
            @PathVariable String memberId,
            @RequestBody DeathDonationRequestDTO dto
    ) {
        return deathDonationService.saveRequest(memberId, dto);
    }

    @PutMapping("/{requestNo}")
    public DeathDonationRequestDTO updateRequest(
            @PathVariable String requestNo,
            @RequestBody DeathDonationRequestDTO dto
    ) {
        dto.setRequestNo(requestNo);
        DeathDonationRequestDTO existing = deathDonationService.getRequestByRequestNo(requestNo);
        return deathDonationService.saveRequest(existing.getMemberId(), dto);
    }

    @PostMapping("/{requestNo}/submit")
    public DeathDonationRequestDTO submitRequest(@PathVariable String requestNo) {
        return deathDonationService.submitRequest(requestNo);
    }

    @PutMapping("/{requestNo}/mark-incomplete")
    public DeathDonationRequestDTO markIncomplete(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        return deathDonationService.markIncomplete(requestNo, body.get("reason"));
    }

    @PutMapping("/{requestNo}/approve")
    public DeathDonationRequestDTO approveRequest(@PathVariable String requestNo) {
        return deathDonationService.approveRequest(requestNo);
    }

    @PutMapping("/{requestNo}/reject")
    public DeathDonationRequestDTO rejectRequest(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        return deathDonationService.rejectRequest(requestNo, body.get("reason"));
    }

    @GetMapping("/relatives-by-certificate")
    public List<DeathDonationRelativeDTO> refreshRelatives(
            @RequestParam String deathCertificateNumber,
            @RequestParam(required = false) String excludeRequestNo
    ) {
        return deathDonationService.refreshRelatives(deathCertificateNumber, excludeRequestNo);
    }

    @GetMapping("/deceased-member/{memberId}/populate")
    public DeathDonationDeceasedPopulateDTO populateDeceasedMember(@PathVariable String memberId) {
        return deathDonationService.populateDeceasedMember(memberId);
    }

    @GetMapping("/{requestNo}/documents")
    public List<DeathDonationDocumentDTO> getDocuments(@PathVariable String requestNo) {
        return deathDonationService.getDocuments(requestNo);
    }

    @PostMapping("/{requestNo}/documents/{documentType}/upload")
    public DeathDonationDocumentDTO uploadDocument(
            @PathVariable String requestNo,
            @PathVariable String documentType,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return deathDonationService.uploadDocument(requestNo, documentType, file);
    }

    @DeleteMapping("/documents/{documentId}")
    public void deleteDocument(@PathVariable Long documentId) {
        deathDonationService.deleteDocument(documentId);
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long documentId) {
        byte[] fileBytes = deathDonationService.downloadDocument(documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(fileBytes);
    }
}

package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.CauseOfDeathDTO;
import com.memberconnect.backend.dto.MemberDeathDocumentDTO;
import com.memberconnect.backend.dto.MemberDeathRecordDTO;
import com.memberconnect.backend.dto.MemberRetirementValidationDTO;
import com.memberconnect.backend.service.MemberDeathRecordService;
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
@RequestMapping("/api/member-death-records")
@CrossOrigin(origins = "http://localhost:3000")
public class MemberDeathRecordController {

    private final MemberDeathRecordService memberDeathRecordService;

    public MemberDeathRecordController(MemberDeathRecordService memberDeathRecordService) {
        this.memberDeathRecordService = memberDeathRecordService;
    }

    @GetMapping("/cause-of-death")
    public List<CauseOfDeathDTO> getCauseOfDeathOptions() {
        return memberDeathRecordService.getCauseOfDeathOptions();
    }

    @GetMapping
    public List<MemberDeathRecordDTO> searchRequests(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String searchKey,
            @RequestParam(required = false, defaultValue = "informedDate") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder
    ) {
        return memberDeathRecordService.searchRequests(
                statuses,
                fromDate,
                toDate,
                searchKey,
                sortBy,
                sortOrder
        );
    }

    @GetMapping("/member/{memberId}")
    public List<MemberDeathRecordDTO> getRecordsByMember(@PathVariable String memberId) {
        return memberDeathRecordService.getRecordsByMember(memberId);
    }

    @GetMapping("/member/{memberId}/active")
    public ResponseEntity<MemberDeathRecordDTO> getActiveRecordForMember(@PathVariable String memberId) {
        MemberDeathRecordDTO record = memberDeathRecordService.getActiveRecordForMember(memberId);
        if (record == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(record);
    }

    @GetMapping("/{recordNo}")
    public MemberDeathRecordDTO getRecord(@PathVariable String recordNo) {
        return memberDeathRecordService.getRecordByRecordNo(recordNo);
    }

    @PostMapping("/{memberId}")
    public MemberDeathRecordDTO saveRecord(
            @PathVariable String memberId,
            @RequestBody MemberDeathRecordDTO dto
    ) {
        return memberDeathRecordService.saveRecord(memberId, dto);
    }

    @PutMapping("/{recordNo}")
    public MemberDeathRecordDTO updateRecord(
            @PathVariable String recordNo,
            @RequestBody MemberDeathRecordDTO dto
    ) {
        dto.setRecordNo(recordNo);
        MemberDeathRecordDTO existing = memberDeathRecordService.getRecordByRecordNo(recordNo);
        return memberDeathRecordService.saveRecord(existing.getMemberId(), dto);
    }

    @PostMapping("/{recordNo}/submit")
    public MemberDeathRecordDTO submitRecord(@PathVariable String recordNo) {
        return memberDeathRecordService.submitRecord(recordNo);
    }

    @PutMapping("/{recordNo}/mark-incomplete")
    public MemberDeathRecordDTO markIncomplete(
            @PathVariable String recordNo,
            @RequestBody Map<String, String> body
    ) {
        return memberDeathRecordService.markIncomplete(recordNo, body.get("reason"));
    }

    @PutMapping("/{recordNo}/change-status")
    public MemberDeathRecordDTO changeStatus(
            @PathVariable String recordNo,
            @RequestBody Map<String, String> body
    ) {
        return memberDeathRecordService.changeStatus(recordNo, body.get("status"));
    }

    @PutMapping("/{recordNo}/approve")
    public MemberDeathRecordDTO approveRecord(@PathVariable String recordNo) {
        return memberDeathRecordService.approveRecord(recordNo);
    }

    @PutMapping("/{recordNo}/reject")
    public MemberDeathRecordDTO rejectRecord(
            @PathVariable String recordNo,
            @RequestBody Map<String, String> body
    ) {
        return memberDeathRecordService.rejectRecord(recordNo, body.get("reason"));
    }

    @GetMapping("/{recordNo}/documents")
    public List<MemberDeathDocumentDTO> getDocuments(@PathVariable String recordNo) {
        return memberDeathRecordService.getDocuments(recordNo);
    }

    @PostMapping("/{recordNo}/documents/{documentType}/upload")
    public MemberDeathDocumentDTO uploadDocument(
            @PathVariable String recordNo,
            @PathVariable String documentType,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return memberDeathRecordService.uploadDocument(recordNo, documentType, file);
    }

    @DeleteMapping("/documents/{documentId}")
    public void deleteDocument(@PathVariable Long documentId) {
        memberDeathRecordService.deleteDocument(documentId);
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long documentId) {
        byte[] fileBytes = memberDeathRecordService.downloadDocument(documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(fileBytes);
    }
}

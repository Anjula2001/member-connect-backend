package com.memberconnect.backend.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.memberconnect.backend.dto.MemberAdHocDocumentDTO;
import com.memberconnect.backend.model.MemberAdHocDocument;
import com.memberconnect.backend.service.MemberAdHocDocumentService;

/**
 * Ad-hoc documents on a Member Profile (Requirement 05, MMD09).
 *
 * The role list here is the outer gate; MemberAdHocDocumentService additionally confines
 * a District Office caller to their own district, which @PreAuthorize cannot express.
 *
 * There is no delete endpoint by design. MMD09 permits removing only files staged in the
 * current Add Documents session, and those are dropped in the browser before Save - so a
 * saved document has no route out, and adding one would create a capability the SRS does
 * not grant.
 */
@RestController
@RequestMapping("/api/members/{memberId}/adhoc-documents")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','SUPER_ADMIN')")
public class MemberAdHocDocumentController {

    private final MemberAdHocDocumentService service;

    public MemberAdHocDocumentController(MemberAdHocDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<MemberAdHocDocumentDTO> list(@PathVariable String memberId) {
        return service.listForMember(memberId);
    }

    /**
     * One file per call. The screen stages a batch and posts them on Save, so a failure
     * on the third file leaves the first two saved rather than losing the lot.
     */
    @PostMapping
    public MemberAdHocDocumentDTO upload(
            @PathVariable String memberId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return service.upload(memberId, file);
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable String memberId,
            @PathVariable Long documentId) {

        MemberAdHocDocument document = service.getForDownload(memberId, documentId);
        byte[] bytes = service.download(document);

        MediaType contentType = document.getFileType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(document.getFileType());

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + document.getFileName() + "\"")
                .body(bytes);
    }
}

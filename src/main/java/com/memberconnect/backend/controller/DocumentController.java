package com.memberconnect.backend.controller;

import com.memberconnect.backend.model.Document;
import com.memberconnect.backend.service.DocumentUploadService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "http://localhost:3000")
public class DocumentController {

    private final DocumentUploadService documentService;

    public DocumentController(DocumentUploadService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload/{requestId}")
    public ResponseEntity<?> uploadDocument(
            @PathVariable Long requestId,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            Document doc = documentService.uploadDocument(requestId, documentType, file);
            return ResponseEntity.ok(doc);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/request/{requestId}")
    public ResponseEntity<?> getDocumentsByRequestId(@PathVariable Long requestId) {
        return ResponseEntity.ok(documentService.getDocumentsByRequestId(requestId));
    }

    @GetMapping("/request/{requestId}/type/{documentType}")
    public ResponseEntity<?> getDocumentsByRequestAndType(
            @PathVariable Long requestId,
            @PathVariable String documentType
    ) {
        return ResponseEntity.ok(
                documentService.getDocumentsByRequestAndType(requestId, documentType)
        );
    }

    @GetMapping("/download/{documentId}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long documentId)
            throws IOException {
        return documentService.downloadDocument(documentId);
    }
}
package com.memberconnect.backend.controller;

import java.io.IOException;
import java.util.List;

import java.io.File;
import java.nio.file.Files;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.memberconnect.backend.dto.RequiredDocumentDTO;
import com.memberconnect.backend.model.UploadedDocument;
import com.memberconnect.backend.service.DocumentService;

@RestController
@RequestMapping("/api/retirement-requests")
@CrossOrigin(origins = "http://localhost:3000")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/{requestId}/required-documents")
    public List<RequiredDocumentDTO> getRequiredDocuments(
            @PathVariable Long requestId,
            @RequestParam String memberId
    ) {
        return documentService.getRequiredDocuments(requestId, memberId);
    }

   @PostMapping("/{requestId}/documents/{requiredDocumentId}/upload")
    public UploadedDocument uploadDocument(
            @PathVariable Long requestId,
            @PathVariable Long requiredDocumentId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return documentService.uploadDocument(requestId, requiredDocumentId, file);
    }

    @GetMapping("/{requestId}/uploaded-documents")
    public List<UploadedDocument> getUploadedDocuments(@PathVariable Long requestId) {
        return documentService.getUploadedDocuments(requestId);
    }

    @DeleteMapping("/documents/{uploadedDocumentId}")
    public void deleteUploadedDocument(@PathVariable Long uploadedDocumentId) {
        documentService.deleteUploadedDocument(uploadedDocumentId);
    }

    @GetMapping("/required-documents-preview")
    public List<RequiredDocumentDTO> getRequiredDocumentsPreview(
            @RequestParam String memberId
    ) {
        return documentService.getRequiredDocuments(0L, memberId);
    }
    
    @GetMapping("/{requestId}/documents/{requiredDocumentId}/uploaded")
    public List<UploadedDocument> getUploadedDocumentsByRequiredDocument(
            @PathVariable Long requestId,
            @PathVariable Long requiredDocumentId
    ) {
        return documentService.getUploadedDocumentsByRequiredDocument(
                requestId,
                requiredDocumentId
        );
    }

    @GetMapping("/documents/{uploadedDocumentId}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long uploadedDocumentId
    ) throws IOException {
        UploadedDocument document = documentService.getUploadedDocumentById(uploadedDocumentId);

        File file = new File(document.getFilePath());

        byte[] fileBytes = Files.readAllBytes(file.toPath());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getFileName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, document.getFileType())
                .body(fileBytes);
    }
}
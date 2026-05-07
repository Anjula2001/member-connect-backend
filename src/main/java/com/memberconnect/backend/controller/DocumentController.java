package com.memberconnect.backend.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

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
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    // Get required documents for a specific request
    @GetMapping("/{requestType}/{requestNo}/required-documents")
    public List<RequiredDocumentDTO> getRequiredDocuments(
            @PathVariable String requestType,
            @PathVariable String requestNo,
            @RequestParam String memberId
    ) {
        String applicationType = mapRequestTypeToApplicationType(requestType);
        return documentService.getRequiredDocuments(requestNo, memberId, applicationType);
    }

    // Get required documents preview
    @GetMapping("/{requestType}/required-documents-preview")
    public List<RequiredDocumentDTO> getRequiredDocumentsPreview(
            @PathVariable String requestType,
            @RequestParam String memberId
    ) {
        String applicationType = mapRequestTypeToApplicationType(requestType);
        return documentService.getRequiredDocuments("0", memberId, applicationType);
    }

    // Upload a document for a specific required document
    @PostMapping("/{requestType}/{requestNo}/documents/{requiredDocumentId}/upload")
    public UploadedDocument uploadDocument(
            @PathVariable String requestType,
            @PathVariable String requestNo,
            @PathVariable Long requiredDocumentId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        String applicationType = mapRequestTypeToApplicationType(requestType);
        return documentService.uploadDocument(
                requestNo,
                requiredDocumentId,
                file,
                applicationType
        );
    }

    // Get uploaded documents for a specific request
    @GetMapping("/{requestType}/{requestNo}/uploaded-documents")
    public List<UploadedDocument> getUploadedDocuments(
            @PathVariable String requestType,
            @PathVariable String requestNo
    ) {
        return documentService.getUploadedDocuments(requestNo);
    }

    // Get uploaded documents for a specific required document
    @GetMapping("/{requestType}/{requestNo}/documents/{requiredDocumentId}/uploaded")
    public List<UploadedDocument> getUploadedDocumentsByRequiredDocument(
            @PathVariable String requestType,
            @PathVariable String requestNo,
            @PathVariable Long requiredDocumentId
    ) {
        return documentService.getUploadedDocumentsByRequiredDocument(
                requestNo,
                requiredDocumentId
        );
    }

    // Delete an uploaded document
    @DeleteMapping("/{requestType}/documents/{uploadedDocumentId}")
    public void deleteUploadedDocument(
            @PathVariable String requestType,
            @PathVariable Long uploadedDocumentId
    ) {
        documentService.deleteUploadedDocument(uploadedDocumentId);
    }

    // Download an uploaded document
    @GetMapping("/{requestType}/documents/{uploadedDocumentId}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable String requestType,
            @PathVariable Long uploadedDocumentId
    ) throws IOException {
        UploadedDocument document =
                documentService.getUploadedDocumentById(uploadedDocumentId);

        File file = new File(document.getFilePath());
        byte[] fileBytes = Files.readAllBytes(file.toPath());

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + document.getFileName() + "\""
                )
                .header(HttpHeaders.CONTENT_TYPE, document.getFileType())
                .body(fileBytes);
    }

    private String mapRequestTypeToApplicationType(String requestType) {
        return switch (requestType) {
            case "retirement-requests" -> "RETIREMENT";
            case "grade5-requests" -> "GRADE5";
            default -> throw new RuntimeException(
                    "Invalid request type: " + requestType
            );
        };
    }
}
package com.memberconnect.backend.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.memberconnect.backend.dto.DocumentSummaryDTO;
import com.memberconnect.backend.dto.RequiredDocumentDTO;
import com.memberconnect.backend.dto.UploadDocumentRequestDTO;
import com.memberconnect.backend.dto.UploadDocumentResponseDTO;
import com.memberconnect.backend.model.UploadedDocument;
import com.memberconnect.backend.service.DocumentService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class DocumentController {

    private final DocumentService documentService;
    private final com.memberconnect.backend.service.S3Service s3Service;

    public DocumentController(DocumentService documentService, com.memberconnect.backend.service.S3Service s3Service) {
        this.documentService = documentService;
        this.s3Service = s3Service;
    }

    // --- Member application document endpoints ---

    @PostMapping("/documents/upload")
    public UploadDocumentResponseDTO uploadDocumentMetadata(@RequestBody UploadDocumentRequestDTO requestDTO) {
        return documentService.uploadDocumentMetadata(requestDTO);
    }

    @GetMapping("/documents/application/{applicationId}")
    public List<UploadDocumentResponseDTO> getDocumentsByApplication(@PathVariable Long applicationId) {
        return documentService.getDocumentsByApplication(applicationId);
    }

    @GetMapping("/documents/summary/{applicationId}")
    public DocumentSummaryDTO getDocumentSummary(@PathVariable Long applicationId) {
        return documentService.getDocumentSummary(applicationId);
    }

    @DeleteMapping("/documents/{id}")
    public void deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
    }

    // --- Retirement / Grade5 document endpoints ---

    @GetMapping("/{requestType}/{requestId}/required-documents")
    public List<RequiredDocumentDTO> getRequiredDocuments(
            @PathVariable String requestType,
            @PathVariable Long requestId,
            @RequestParam String memberId
    ) {
        String applicationType = mapRequestTypeToApplicationType(requestType);
        return documentService.getRequiredDocuments(requestId, memberId, applicationType);
    }

    @GetMapping("/{requestType}/required-documents-preview")
    public List<RequiredDocumentDTO> getRequiredDocumentsPreview(
            @PathVariable String requestType,
            @RequestParam String memberId
    ) {
        String applicationType = mapRequestTypeToApplicationType(requestType);
        return documentService.getRequiredDocuments(0L, memberId, applicationType);
    }

    @PostMapping("/{requestType}/{requestId}/documents/{requiredDocumentId}/upload")
    public UploadedDocument uploadDocument(
            @PathVariable String requestType,
            @PathVariable Long requestId,
            @PathVariable Long requiredDocumentId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        String applicationType = mapRequestTypeToApplicationType(requestType);
        return documentService.uploadDocument(
                requestId,
                requiredDocumentId,
                file,
                applicationType
        );
    }

    @GetMapping("/{requestType}/{requestId}/uploaded-documents")
    public List<UploadedDocument> getUploadedDocuments(
            @PathVariable String requestType,
            @PathVariable Long requestId
    ) {
        return documentService.getUploadedDocuments(requestId);
    }

    @PostMapping("/file/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        String fileName = s3Service.uploadFile(file);
        return ResponseEntity.ok(fileName);
    }

    @GetMapping("/file/download")
    public ResponseEntity<byte[]> downloadGenericFile(@RequestParam("fileName") String fileName) {
        byte[] fileBytes = s3Service.downloadFile(fileName);
        return ResponseEntity.ok().body(fileBytes);
    }

    @GetMapping("/{requestType}/{requestId}/documents/{requiredDocumentId}/uploaded")
    public List<UploadedDocument> getUploadedDocumentsByRequiredDocument(
            @PathVariable String requestType,
            @PathVariable Long requestId,
            @PathVariable Long requiredDocumentId
    ) {
        return documentService.getUploadedDocumentsByRequiredDocument(
                requestId,
                requiredDocumentId
        );
    }

    @DeleteMapping("/{requestType}/documents/{uploadedDocumentId}/file")
    public void deleteUploadedDocument(
            @PathVariable String requestType,
            @PathVariable Long uploadedDocumentId
    ) {
        documentService.deleteUploadedDocument(uploadedDocumentId);
    }

    @GetMapping("/{requestType}/documents/{uploadedDocumentId}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable String requestType,
            @PathVariable Long uploadedDocumentId
    ) throws IOException {
        UploadedDocument document =
                documentService.getUploadedDocumentById(uploadedDocumentId);

        byte[] fileBytes = s3Service.downloadFile(document.getFilePath());

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

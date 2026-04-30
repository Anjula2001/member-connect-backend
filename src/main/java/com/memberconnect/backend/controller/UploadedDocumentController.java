package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.UploadedDocumentDisplayDto;
import com.memberconnect.backend.dto.UploadedDocumentUploadDto;
import com.memberconnect.backend.model.UploadedDocument;
import com.memberconnect.backend.service.UploadedDocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/uploaded-documents")
@CrossOrigin(origins = "http://localhost:3000")
public class UploadedDocumentController {

    private final UploadedDocumentService service;

    public UploadedDocumentController(UploadedDocumentService service) {
        this.service = service;
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadedDocumentUploadDto> upload(
            @RequestParam String requestId,
            @RequestParam Long requiredDocumentId,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            UploadedDocument ud = service.upload(requestId, requiredDocumentId, file);
            return ResponseEntity.ok(new UploadedDocumentUploadDto(ud.getId(), ud.getRequestId(), ud.getRequiredDocumentId(), "Uploaded", true));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new UploadedDocumentUploadDto(null, requestId, requiredDocumentId, "Error: " + e.getMessage(), false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new UploadedDocumentUploadDto(null, requestId, requiredDocumentId, e.getMessage(), false));
        }
    }

    @GetMapping("/by-request")
    public ResponseEntity<?> listByRequest(@RequestParam String requestId) {
        try {
            List<UploadedDocumentDisplayDto> list = service.listByRequest(requestId);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", "Failed to load documents"));
        }
    }

    @GetMapping("/by-required")
    public ResponseEntity<?> listByRequired(@RequestParam String requestId, @RequestParam Long requiredDocumentId) {
        try {
            List<UploadedDocumentDisplayDto> list = service.listByRequestAndRequired(requestId, requiredDocumentId);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", "Failed to load documents"));
        }
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @RequestParam String requestId) {
        try {
            byte[] content = service.download(id, requestId);
            UploadedDocument d = service.getDetails(id, requestId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.getFileName() + "\"")
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, @RequestParam String requestId) {
        try {
            service.delete(id, requestId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Deleted"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}

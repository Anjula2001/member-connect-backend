package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.DocumentSummaryDTO;
import com.memberconnect.backend.dto.UploadDocumentRequestDTO;
import com.memberconnect.backend.dto.UploadDocumentResponseDTO;
import com.memberconnect.backend.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @PostMapping("/upload")
    public UploadDocumentResponseDTO uploadDocumentMetadata(@RequestBody UploadDocumentRequestDTO requestDTO) {
        return documentService.uploadDocumentMetadata(requestDTO);
    }

    @GetMapping("/application/{applicationId}")
    public List<UploadDocumentResponseDTO> getDocumentsByApplication(@PathVariable Long applicationId) {
        return documentService.getDocumentsByApplication(applicationId);
    }

    @GetMapping("/summary/{applicationId}")
    public DocumentSummaryDTO getDocumentSummary(@PathVariable Long applicationId) {
        return documentService.getDocumentSummary(applicationId);
    }
}

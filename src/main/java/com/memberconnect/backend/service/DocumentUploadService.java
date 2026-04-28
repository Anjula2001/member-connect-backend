package com.memberconnect.backend.service;

import com.memberconnect.backend.model.Document;
import com.memberconnect.backend.model.UniversityScholarshipRequest;
import com.memberconnect.backend.repository.DocumentUploadRepository;
import com.memberconnect.backend.repository.UniversityScholarshipRequestRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DocumentUploadService {

    private final DocumentUploadRepository documentUploadRepository;
    private final UniversityScholarshipRequestRepository requestRepository;

    public DocumentUploadService(
            DocumentUploadRepository documentUploadRepository,
            UniversityScholarshipRequestRepository requestRepository
    ) {
        this.documentUploadRepository = documentUploadRepository;
        this.requestRepository = requestRepository;
    }

    public Document uploadDocument(Long requestId, String documentType, MultipartFile file)
            throws IOException {

        UniversityScholarshipRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        String uploadDir = "uploads/";
        File folder = new File(uploadDir);

        if (!folder.exists()) {
            folder.mkdirs();
        }

        String originalFileName = file.getOriginalFilename();
        String safeFileName = System.currentTimeMillis() + "_" + originalFileName;
        String filePath = uploadDir + safeFileName;

        file.transferTo(new File(filePath));

        Document doc = new Document();
        doc.setRequest(request);
        doc.setDocumentType(documentType);
        doc.setFileName(originalFileName);
        doc.setFilePath(filePath);
        doc.setFileType(file.getContentType());
        doc.setUploadedAt(LocalDateTime.now());

        return documentUploadRepository.save(doc);
    }

    public List<Document> getDocumentsByRequestId(Long requestId) {
        return documentUploadRepository.findByRequest_Id(requestId);
    }

    public List<Document> getDocumentsByRequestAndType(Long requestId, String documentType) {
        return documentUploadRepository.findByRequest_IdAndDocumentType(requestId, documentType);
    }

    public ResponseEntity<Resource> downloadDocument(Long documentId) throws IOException {
        Document document = documentUploadRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        Path path = Paths.get(document.getFilePath()).toAbsolutePath().normalize();
        Resource resource = new UrlResource(path.toUri());

        if (!resource.exists()) {
            throw new RuntimeException("File not found");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + document.getFileName() + "\""
                )
                .body(resource);
    }
}

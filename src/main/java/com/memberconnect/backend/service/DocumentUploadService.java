package com.memberconnect.backend.service;

import com.memberconnect.backend.model.Document;
import com.memberconnect.backend.model.UniversityScholarshipRequest;
import com.memberconnect.backend.repository.DocumentUploadRepository;
import com.memberconnect.backend.repository.UniversityScholarshipRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
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
            folder.mkdir();
        }

        String filePath = uploadDir + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        file.transferTo(new File(filePath));

        Document doc = new Document();
        doc.setRequest(request);
        doc.setDocumentType(documentType);
        doc.setFileName(file.getOriginalFilename());
        doc.setFilePath(filePath);

        return documentUploadRepository.save(doc);
    }

    public List<Document> getDocumentsByRequestId(Long requestId) {
        return documentUploadRepository.findByRequest_Id(requestId);
    }
}
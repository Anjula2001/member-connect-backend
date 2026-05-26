package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.UploadedDocumentDisplayDto;
import com.memberconnect.backend.model.UploadedDocument;
import com.memberconnect.backend.repository.UploadedDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UploadedDocumentService {

    private final UploadedDocumentRepository repository;
    private final S3Service s3Service;
    
    public UploadedDocumentService(UploadedDocumentRepository repository, S3Service s3Service) {
        this.repository = repository;
        this.s3Service = s3Service;
    }

    // Upload a document for a specific request and required document type
    public UploadedDocument upload(String requestId, Long requiredDocumentId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String fileName = s3Service.uploadFile(file);

        UploadedDocument ud = new UploadedDocument();
        ud.setRequestId(requestId);
        ud.setRequiredDocumentId(requiredDocumentId);
        ud.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : fileName);
        ud.setFileType(file.getContentType());
        ud.setFilePath(fileName);
        ud.setUploadedAt(LocalDateTime.now());

        return repository.save(ud);
    }
    
    // List uploaded documents for a specific request
    public List<UploadedDocumentDisplayDto> listByRequest(String requestId) {
        return repository.findByRequestId(requestId)
                .stream()
            .map(d -> new UploadedDocumentDisplayDto(
                d.getId(),
                d.getRequestId(),
                d.getRequiredDocumentId(),
                d.getFileName(),
                d.getFilePath(),
                d.getFileType(),
                d.getUploadedAt()
            ))
                .collect(Collectors.toList());
    }

    // List uploaded documents for a specific request and required document type
    public List<UploadedDocumentDisplayDto> listByRequestAndRequired(String requestId, Long requiredDocumentId) {
        return repository.findByRequestIdAndRequiredDocumentId(requestId, requiredDocumentId)
                .stream()
            .map(d -> new UploadedDocumentDisplayDto(
                d.getId(),
                d.getRequestId(),
                d.getRequiredDocumentId(),
                d.getFileName(),
                d.getFilePath(),
                d.getFileType(),
                d.getUploadedAt()
            ))
                .collect(Collectors.toList());
    }

    // Download a specific uploaded document by ID and request ID
    public byte[] download(Long documentId, String requestId) throws IOException {
        Optional<UploadedDocument> opt = repository.findByIdAndRequestId(documentId, requestId);
        if (opt.isEmpty()) throw new IllegalArgumentException("Document not found");
        UploadedDocument d = opt.get();
        return s3Service.downloadFile(d.getFilePath());
    }

    // Get details of a specific uploaded document by ID and request ID
    public UploadedDocument getDetails(Long documentId, String requestId) {
        return repository.findByIdAndRequestId(documentId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    // Delete a specific uploaded document by ID and request ID
    public void delete(Long documentId, String requestId) throws IOException {
        Optional<UploadedDocument> opt = repository.findByIdAndRequestId(documentId, requestId);
        if (opt.isEmpty()) throw new IllegalArgumentException("Document not found");
        UploadedDocument d = opt.get();
        s3Service.deleteFile(d.getFilePath());
        repository.delete(d);
    }

}

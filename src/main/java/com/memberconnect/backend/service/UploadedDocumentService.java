package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.UploadedDocumentDisplayDto;
import com.memberconnect.backend.model.UploadedDocument;
import com.memberconnect.backend.repository.UploadedDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UploadedDocumentService {

    private final UploadedDocumentRepository repository;

    @Value("${file.upload.dir:uploads}")
    private String uploadDir;
    
    public UploadedDocumentService(UploadedDocumentRepository repository) {
        this.repository = repository;
    }

    // Upload a document for a specific request and required document type
    public UploadedDocument upload(String requestId, Long requiredDocumentId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        Path requestPath = Paths.get(uploadDir, String.valueOf(requestId));
        Files.createDirectories(requestPath);

        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String unique = UUID.randomUUID().toString() + ext;
        Path dest = requestPath.resolve(unique);

        Files.copy(file.getInputStream(), dest);

        UploadedDocument ud = new UploadedDocument();
        ud.setRequestId(requestId);
        ud.setRequiredDocumentId(requiredDocumentId);
        ud.setFileName(original != null ? original : unique);
        ud.setFileType(file.getContentType());
        ud.setFilePath(dest.toString());
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
        File f = new File(d.getFilePath());
        if (!f.exists()) throw new IllegalArgumentException("File missing on server");
        return Files.readAllBytes(f.toPath());
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
        File f = new File(d.getFilePath());
        if (f.exists()) Files.delete(f.toPath());
        repository.delete(d);
    }

}

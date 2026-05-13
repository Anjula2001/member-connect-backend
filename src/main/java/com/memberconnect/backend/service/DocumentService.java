package com.memberconnect.backend.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.memberconnect.backend.dto.DocumentSummaryDTO;
import com.memberconnect.backend.dto.RequiredDocumentDTO;
import com.memberconnect.backend.dto.UploadDocumentRequestDTO;
import com.memberconnect.backend.dto.UploadDocumentResponseDTO;
import com.memberconnect.backend.model.UploadDocument;
import com.memberconnect.backend.model.RequiredDocument;
import com.memberconnect.backend.model.UploadedDocument;
import com.memberconnect.backend.repository.MemberApplicationRepository;
import com.memberconnect.backend.repository.MinorSavingsAccountRepository;
import com.memberconnect.backend.repository.RequiredDocumentRepository;
import com.memberconnect.backend.repository.UploadDocumentRepository;
import com.memberconnect.backend.repository.UploadedDocumentRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
@Transactional
public class DocumentService {

    private static final Set<String> MANDATORY_DOCUMENT_TYPES = Set.of(
            "NIC_COPY",
            "APPOINTMENT_LETTER",
            "PAYSLIP_COPY"
    );

    private final RequiredDocumentRepository requiredDocumentRepository;
    private final UploadedDocumentRepository uploadedDocumentRepository;
    private final MinorSavingsAccountRepository minorSavingsAccountRepository;

    @Autowired
    private UploadDocumentRepository uploadDocumentRepository;

    @Autowired
    private MemberApplicationRepository memberApplicationRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private S3Service s3Service;

    @PersistenceContext
    private EntityManager entityManager;

    public DocumentService(
            RequiredDocumentRepository requiredDocumentRepository,
            UploadedDocumentRepository uploadedDocumentRepository,
            MinorSavingsAccountRepository minorSavingsAccountRepository
    ) {
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.uploadedDocumentRepository = uploadedDocumentRepository;
        this.minorSavingsAccountRepository = minorSavingsAccountRepository;
    }

    // --- Member application document methods ---

    public UploadDocumentResponseDTO uploadDocumentMetadata(UploadDocumentRequestDTO requestDTO) {
        Long applicationId = requestDTO.getApplicationId();
        if (applicationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application ID is required");
        }

        if (requestDTO.getDocumentType() == null || requestDTO.getDocumentType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document type is required");
        }

        memberApplicationRepository.findById(Objects.requireNonNull(applicationId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        UploadDocument uploadDocument = new UploadDocument();
        uploadDocument.setApplicationId(applicationId);
        uploadDocument.setDocumentType(requestDTO.getDocumentType());
        uploadDocument.setFileName(requestDTO.getFileName());
        uploadDocument.setFileType(requestDTO.getFileType());
        uploadDocument.setStoragePath(requestDTO.getStoragePath());
        uploadDocument.setFileSize(requestDTO.getFileSize());
        uploadDocument.setDocumentId("DOC-" + UUID.randomUUID());
        uploadDocument.setUploadedAt(LocalDateTime.now());
        uploadDocument.setId(null);

        entityManager.persist(uploadDocument);
        entityManager.flush();

        UploadDocumentResponseDTO responseDTO = new UploadDocumentResponseDTO();
        responseDTO.setId(uploadDocument.getId());
        responseDTO.setDocumentId(uploadDocument.getDocumentId());
        responseDTO.setApplicationId(uploadDocument.getApplicationId());
        responseDTO.setDocumentType(uploadDocument.getDocumentType());
        responseDTO.setFileName(uploadDocument.getFileName());
        responseDTO.setFileType(uploadDocument.getFileType());
        responseDTO.setStoragePath(uploadDocument.getStoragePath());
        responseDTO.setFileSize(uploadDocument.getFileSize());
        responseDTO.setUploadedAt(uploadDocument.getUploadedAt());
        return responseDTO;
    }

    public List<UploadDocumentResponseDTO> getDocumentsByApplication(Long applicationId) {
        return uploadDocumentRepository.findByApplicationId(applicationId)
                .stream()
                .map(doc -> modelMapper.map(doc, UploadDocumentResponseDTO.class))
                .toList();
    }

    public DocumentSummaryDTO getDocumentSummary(Long applicationId) {
        List<UploadDocument> uploadedDocs = uploadDocumentRepository.findByApplicationId(applicationId);

        Set<String> uploadedMandatoryTypes = uploadedDocs.stream()
                .map(UploadDocument::getDocumentType)
                .filter(MANDATORY_DOCUMENT_TYPES::contains)
                .collect(Collectors.toSet());

        return new DocumentSummaryDTO(
                MANDATORY_DOCUMENT_TYPES.size(),
                uploadedMandatoryTypes.size(),
                uploadedDocs.size()
        );
    }

    public void deleteDocument(Long id) {
        UploadDocument doc = uploadDocumentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        if (doc.getStoragePath() != null && !doc.getStoragePath().isBlank()) {
            try {
                Path filePath = Paths.get(doc.getStoragePath());
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                // Log but don't fail — remove the DB record regardless
                System.err.println("Warning: could not delete file at " + doc.getStoragePath() + ": " + e.getMessage());
            }
        }

        uploadDocumentRepository.deleteById(id);
    }
}

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
<<<<<<< HEAD
=======

    private static final Set<String> MANDATORY_DOCUMENT_TYPES = Set.of(
            "NIC_COPY",
            "APPOINTMENT_LETTER",
            "PAYSLIP_COPY"
    );

>>>>>>> 4a387f07f4d91b8e58ab1c67bde5db83107373d5
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final UploadedDocumentRepository uploadedDocumentRepository;
    private final MinorSavingsAccountRepository minorSavingsAccountRepository;

    @Autowired
    private UploadDocumentRepository uploadDocumentRepository;

    @Autowired
    private MemberApplicationRepository memberApplicationRepository;

    @Autowired
    private ModelMapper modelMapper;

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

<<<<<<< HEAD
    // Get required documents for a specific request
=======
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
                System.err.println("Warning: could not delete file at " + doc.getStoragePath() + ": " + e.getMessage());
            }
        }

        uploadDocumentRepository.deleteById(id);
    }

    // --- Retirement / Grade5 document methods ---

>>>>>>> 4a387f07f4d91b8e58ab1c67bde5db83107373d5
    public List<RequiredDocumentDTO> getRequiredDocuments(
            String requestNo,
            String memberId,
            String applicationType
    ) {
        List<String> types = new ArrayList<>();

        types.add(applicationType);

        if ("RETIREMENT".equals(applicationType)) {
            boolean hasMinorSavings =
                    !minorSavingsAccountRepository.findByMemberId(memberId).isEmpty();

            if (hasMinorSavings) {
                types.add("RETIREMENT_MINOR");
            }
        }

        List<RequiredDocument> requiredDocs = requiredDocumentRepository.findByApplicationTypeIn(types);

        return requiredDocs.stream()
                .map(doc -> new RequiredDocumentDTO(
                        doc.getId(),
                        doc.getDocumentName(),
                        doc.isMandatory(),
                        uploadedDocumentRepository.existsByRequestNoAndRequiredDocumentId(
                                requestNo,
                                doc.getId()
                        )
                ))
                .toList();
    }

    // Upload a document for a specific required document
    public UploadedDocument uploadDocument(
            String requestNo,
            Long requiredDocumentId,
            MultipartFile file,
            String applicationType
    ) {
        try {
            if (file == null || file.isEmpty()) {
                throw new RuntimeException("Please select a file to upload");
            }

            String uploadDir = System.getProperty("user.dir")
                    + File.separator + "uploads"
                    + File.separator + applicationType.toLowerCase()
                    + File.separator + requestNo;

            File dir = new File(uploadDir);

            if (!dir.exists()) {
                boolean created = dir.mkdirs();

                if (!created) {
                    throw new RuntimeException("Could not create upload folder");
                }
            }

            String originalFileName = file.getOriginalFilename();
            String safeFileName = System.currentTimeMillis() + "_" + originalFileName;

            File destinationFile = new File(dir, safeFileName);

            file.transferTo(destinationFile);

            UploadedDocument uploaded = new UploadedDocument();
            uploaded.setRequestNo(requestNo);
            uploaded.setRequiredDocumentId(requiredDocumentId);
            uploaded.setFileName(originalFileName);
            uploaded.setFileType(file.getContentType());
            uploaded.setFilePath(destinationFile.getAbsolutePath());
            uploaded.setUploadedAt(LocalDateTime.now());

            return uploadedDocumentRepository.save(uploaded);

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload document: " + e.getMessage());
        }
    }

    // Get uploaded documents for a specific request
    public List<UploadedDocument> getUploadedDocuments(String requestNo) {
        return uploadedDocumentRepository.findByRequestNo(requestNo);
    }

    // Get uploaded documents for a specific required document
    public List<UploadedDocument> getUploadedDocumentsByRequiredDocument(
            String requestNo,
            Long requiredDocumentId
    ) {
        return uploadedDocumentRepository.findByRequestNoAndRequiredDocumentId(
                requestNo,
                requiredDocumentId
        );
    }

    //Delete uploaded document
    public void deleteUploadedDocument(Long uploadedDocumentId) {
        uploadedDocumentRepository.deleteById(uploadedDocumentId);
    }
    
    //check all mandatory document uploaded
    public boolean allMandatoryDocumentsUploaded(
            String requestNo,
            String memberId,
            String applicationType
    ) {
        List<RequiredDocumentDTO> docs =
                getRequiredDocuments(requestNo, memberId, applicationType);

        return docs.stream()
                .filter(RequiredDocumentDTO::isMandatory)
                .allMatch(RequiredDocumentDTO::isUploaded);
    }

    public UploadedDocument getUploadedDocumentById(Long uploadedDocumentId) {
        return uploadedDocumentRepository.findById(uploadedDocumentId)
                .orElseThrow(() -> new RuntimeException("Uploaded document not found"));
    }
}

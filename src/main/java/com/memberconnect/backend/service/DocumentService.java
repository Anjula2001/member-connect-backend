package com.memberconnect.backend.service;

import java.io.IOException;
import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.HashSet;
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

    public List<RequiredDocumentDTO> getRequiredDocuments(Long requestId, String memberId, String applicationType) {
        List<RequiredDocument> requiredDocuments = requiredDocumentRepository.findByApplicationTypeIn(List.of(applicationType));
        Set<Long> uploadedRequiredDocumentIds = uploadedDocumentRepository.findByRequestNo(String.valueOf(requestId))
                .stream()
                .map(UploadedDocument::getRequiredDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return requiredDocuments.stream()
                .map(requiredDocument -> new RequiredDocumentDTO(
                        requiredDocument.getId(),
                        requiredDocument.getDocumentName(),
                        requiredDocument.isMandatory(),
                        uploadedRequiredDocumentIds.contains(requiredDocument.getId())
                ))
                .toList();
    }

    public UploadedDocument uploadDocument(
            Long requestId,
            Long requiredDocumentId,
            MultipartFile file,
            String applicationType
    ) throws IOException {
        if (requestId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request ID is required");
        }

        if (requiredDocumentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required document ID is required");
        }

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }

        RequiredDocument requiredDocument = requiredDocumentRepository.findById(requiredDocumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Required document not found"));

        if (requiredDocument.getApplicationType() != null
                && applicationType != null
                && !requiredDocument.getApplicationType().equalsIgnoreCase(applicationType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required document does not match request type");
        }

        List<UploadedDocument> existingDocuments = uploadedDocumentRepository.findByRequestNoAndRequiredDocumentId(
                String.valueOf(requestId),
                requiredDocumentId
        );

        if (!existingDocuments.isEmpty()) {
            UploadedDocument existingDocument = existingDocuments.getFirst();
            if (existingDocument.getFilePath() != null && !existingDocument.getFilePath().isBlank()) {
                s3Service.deleteFile(existingDocument.getFilePath());
            }
            uploadedDocumentRepository.delete(existingDocument);
        }

        String fileKey = s3Service.uploadFile(file);

        UploadedDocument uploadedDocument = new UploadedDocument();
        uploadedDocument.setRequestId(String.valueOf(requestId));
        uploadedDocument.setRequiredDocumentId(requiredDocumentId);
        uploadedDocument.setFileName(file.getOriginalFilename());
        uploadedDocument.setFileType(file.getContentType());
        uploadedDocument.setFilePath(fileKey);
        uploadedDocument.setUploadedAt(LocalDateTime.now());

        return uploadedDocumentRepository.save(uploadedDocument);
    }

    public List<UploadedDocument> getUploadedDocuments(Long requestId) {
        return uploadedDocumentRepository.findByRequestNo(String.valueOf(requestId));
    }

    public List<UploadedDocument> getUploadedDocumentsByRequiredDocument(Long requestId, Long requiredDocumentId) {
        return uploadedDocumentRepository.findByRequestNoAndRequiredDocumentId(String.valueOf(requestId), requiredDocumentId);
    }

    public void deleteUploadedDocument(Long uploadedDocumentId) {
        UploadedDocument document = getUploadedDocumentById(uploadedDocumentId);

        if (document.getFilePath() != null && !document.getFilePath().isBlank()) {
            s3Service.deleteFile(document.getFilePath());
        }

        uploadedDocumentRepository.delete(document);
    }

    public UploadedDocument getUploadedDocumentById(Long uploadedDocumentId) {
        return uploadedDocumentRepository.findById(uploadedDocumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Uploaded document not found"));
    }

    public boolean allMandatoryDocumentsUploaded(Long requestId, String memberId, String applicationType) {
        List<RequiredDocumentDTO> requiredDocuments = getRequiredDocuments(requestId, memberId, applicationType);
        return requiredDocuments.stream()
                .filter(RequiredDocumentDTO::isMandatory)
                .allMatch(RequiredDocumentDTO::isUploaded);
    }

    public boolean allMandatoryDocumentsUploaded(String requestNo, String memberId, String applicationType) {
        List<RequiredDocumentDTO> requiredDocuments = getRequiredDocuments(requestNo, memberId, applicationType);
        return requiredDocuments.stream()
                .filter(RequiredDocumentDTO::isMandatory)
                .allMatch(RequiredDocumentDTO::isUploaded);
    }

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

        uploadDocumentRepository.deleteById(id);
    }
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

        if ("TERMINATION".equals(applicationType)) {
            boolean hasMinorSavings =
                    !minorSavingsAccountRepository.findByMemberId(memberId).isEmpty();

            if (hasMinorSavings) {
                types.add("TERMINATION_MINOR");
            }
        }

        // MMT18: "The mandatory supporting documents might increase if there are
        // Minor Savings Accounts for the Member that needs to be closed."
        if ("MEMBER_DEATH".equals(applicationType)) {
            boolean hasMinorSavings =
                    !minorSavingsAccountRepository.findByMemberId(memberId).isEmpty();

            if (hasMinorSavings) {
                types.add("MEMBER_DEATH_MINOR");
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

            List<UploadedDocument> existingDocuments = uploadedDocumentRepository.findByRequestNoAndRequiredDocumentId(
                    requestNo,
                    requiredDocumentId
            );

            if (!existingDocuments.isEmpty()) {
                UploadedDocument existingDocument = existingDocuments.getFirst();
                if (existingDocument.getFilePath() != null && !existingDocument.getFilePath().isBlank()) {
                    s3Service.deleteFile(existingDocument.getFilePath());
                }
                uploadedDocumentRepository.delete(existingDocument);
            }

            String fileKey = s3Service.uploadFile(file);

            UploadedDocument uploaded = new UploadedDocument();
            uploaded.setRequestNo(requestNo);
            uploaded.setRequiredDocumentId(requiredDocumentId);
            uploaded.setFileName(file.getOriginalFilename());
            uploaded.setFileType(file.getContentType());
            uploaded.setFilePath(fileKey);
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
}

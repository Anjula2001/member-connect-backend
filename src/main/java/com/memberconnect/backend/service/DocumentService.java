package com.memberconnect.backend.service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.memberconnect.backend.dto.RequiredDocumentDTO;
import com.memberconnect.backend.model.RequiredDocument;
import com.memberconnect.backend.model.UploadedDocument;
import com.memberconnect.backend.repository.MinorSavingsAccountRepository;
import com.memberconnect.backend.repository.RequiredDocumentRepository;
import com.memberconnect.backend.repository.UploadedDocumentRepository;

@Service
public class DocumentService {
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final UploadedDocumentRepository uploadedDocumentRepository;
    private final MinorSavingsAccountRepository minorSavingsAccountRepository;

    public DocumentService(
            RequiredDocumentRepository requiredDocumentRepository,
            UploadedDocumentRepository uploadedDocumentRepository,
            MinorSavingsAccountRepository minorSavingsAccountRepository
    ) {
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.uploadedDocumentRepository = uploadedDocumentRepository;
        this.minorSavingsAccountRepository = minorSavingsAccountRepository;
    }

    // Get required documents for a specific request
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
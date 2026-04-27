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

    public List<RequiredDocumentDTO> getRequiredDocuments(
            Long requestId,
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

        List<RequiredDocument> requiredDocs =
                requiredDocumentRepository.findByApplicationTypeIn(types);

        return requiredDocs.stream()
                .map(doc -> new RequiredDocumentDTO(
                        doc.getId(),
                        doc.getDocumentName(),
                        doc.isMandatory(),
                        uploadedDocumentRepository.existsByRequestIdAndRequiredDocumentId(
                                requestId,
                                doc.getId()
                        )
                ))
                .toList();
    }

    public UploadedDocument uploadDocument(
            Long requestId,
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
                    + File.separator + requestId;

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
            uploaded.setRequestId(requestId);
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

    public List<UploadedDocument> getUploadedDocuments(Long requestId) {
        return uploadedDocumentRepository.findByRequestId(requestId);
    }

    public List<UploadedDocument> getUploadedDocumentsByRequiredDocument(
            Long requestId,
            Long requiredDocumentId
    ) {
        return uploadedDocumentRepository.findByRequestIdAndRequiredDocumentId(
                requestId,
                requiredDocumentId
        );
    }

    public void deleteUploadedDocument(Long uploadedDocumentId) {
        uploadedDocumentRepository.deleteById(uploadedDocumentId);
    }

    public boolean allMandatoryDocumentsUploaded(
            Long requestId,
            String memberId,
            String applicationType
    ) {
        List<RequiredDocumentDTO> docs =
                getRequiredDocuments(requestId, memberId, applicationType);

        return docs.stream()
                .filter(RequiredDocumentDTO::isMandatory)
                .allMatch(RequiredDocumentDTO::isUploaded);
    }

    public UploadedDocument getUploadedDocumentById(Long uploadedDocumentId) {
        return uploadedDocumentRepository.findById(uploadedDocumentId)
                .orElseThrow(() -> new RuntimeException("Uploaded document not found"));
    }
}
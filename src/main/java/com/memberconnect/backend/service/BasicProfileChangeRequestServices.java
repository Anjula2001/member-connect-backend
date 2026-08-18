package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.BasicProfileChangeRequestDTO;
import com.memberconnect.backend.model.BasicProfileChangeRequest;
import com.memberconnect.backend.repository.BasicProfileChangeRequestRepo;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BasicProfileChangeRequestServices {

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private BasicProfileChangeRequestRepo basicProfileChangeRequestRepo;

    public List<BasicProfileChangeRequestDTO> getBasicProfileChangeRequests(){
        List<BasicProfileChangeRequest> basicProfileChangeRequests = basicProfileChangeRequestRepo.findAll();
        return modelMapper.map(basicProfileChangeRequests,new TypeToken<List<BasicProfileChangeRequestDTO>>(){}.getType());
    }

    public BasicProfileChangeRequestDTO getRequestById(Integer id) {
        Optional<BasicProfileChangeRequest> optionalEntity = basicProfileChangeRequestRepo.findById(id);

        if (optionalEntity.isPresent()) {
            return modelMapper.map(optionalEntity.get(), BasicProfileChangeRequestDTO.class);
        } else {
            return null;
        }
    }

    public String saveBasicProfileChangeRequest(BasicProfileChangeRequestDTO basicProfileChangeRequestDTO){
        BasicProfileChangeRequest entity = modelMapper.map(basicProfileChangeRequestDTO, BasicProfileChangeRequest.class);
        basicProfileChangeRequestRepo.save(entity);
        return "success";
    }

    public String saveWithDocument(BasicProfileChangeRequestDTO dto, org.springframework.web.multipart.MultipartFile file) {
        BasicProfileChangeRequest entity = modelMapper.map(dto, BasicProfileChangeRequest.class);
        handleFileUpload(entity, file);
        basicProfileChangeRequestRepo.save(entity);
        return "success";
    }

    public BasicProfileChangeRequestDTO updateProfileRequest(Integer id, BasicProfileChangeRequestDTO dto) {
        BasicProfileChangeRequest existingEntity = basicProfileChangeRequestRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found with id: " + id));
        // Preserve the original createdDate
        java.time.LocalDateTime originalCreatedDate = existingEntity.getCreatedDate();
        modelMapper.map(dto, existingEntity);
        existingEntity.setId(id);
        existingEntity.setCreatedDate(originalCreatedDate);
        BasicProfileChangeRequest updatedEntity = basicProfileChangeRequestRepo.save(existingEntity);
        return modelMapper.map(updatedEntity, BasicProfileChangeRequestDTO.class);
    }

    public BasicProfileChangeRequestDTO updateStatus(Integer id, com.memberconnect.backend.enums.ApplicationStatus status) {
        BasicProfileChangeRequest existingEntity = basicProfileChangeRequestRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found with id: " + id));
        existingEntity.setNewStatus(status);
        BasicProfileChangeRequest updatedEntity = basicProfileChangeRequestRepo.save(existingEntity);
        return modelMapper.map(updatedEntity, BasicProfileChangeRequestDTO.class);
    }

    public BasicProfileChangeRequestDTO updateWithDocument(Integer id, BasicProfileChangeRequestDTO dto, org.springframework.web.multipart.MultipartFile file) {
        BasicProfileChangeRequest existingEntity = basicProfileChangeRequestRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found with id: " + id));
        // Preserve the original createdDate
        java.time.LocalDateTime originalCreatedDate = existingEntity.getCreatedDate();
        
        // Save current file storage path in case we replace it
        String oldFileStoragePath = existingEntity.getDocumentStoragePath();

        modelMapper.map(dto, existingEntity);
        existingEntity.setId(id);
        existingEntity.setCreatedDate(originalCreatedDate);

        if (file != null && !file.isEmpty()) {
            // Delete old file if present
            deleteFileIfExists(oldFileStoragePath);
            // Handle new upload
            handleFileUpload(existingEntity, file);
        } else if (dto.getDocumentStoragePath() == null || dto.getDocumentStoragePath().isBlank()) {
            // If the frontend explicitly cleared the storage path, delete the physical file too
            deleteFileIfExists(oldFileStoragePath);
            existingEntity.setDocumentStoragePath(null);
            existingEntity.setDocumentFileName(null);
            existingEntity.setDocumentFileType(null);
            existingEntity.setDocumentFileSize(null);
            existingEntity.setDocumentType(null);
        } else {
            // Keep the old file path if no new file is provided and path is not cleared
            existingEntity.setDocumentStoragePath(oldFileStoragePath);
        }

        BasicProfileChangeRequest updatedEntity = basicProfileChangeRequestRepo.save(existingEntity);
        return modelMapper.map(updatedEntity, BasicProfileChangeRequestDTO.class);
    }

    public String deleteProfileRequest(Integer id) {
        BasicProfileChangeRequest request = basicProfileChangeRequestRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Cannot delete: Request not found with id: " + id));
        
        // Delete associated physical file
        deleteFileIfExists(request.getDocumentStoragePath());
        
        basicProfileChangeRequestRepo.deleteById(id);
        return "Successfully deleted request";
    }

    private void handleFileUpload(BasicProfileChangeRequest entity, org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        try {
            String uploadDir = "uploads";
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(uploadDir);
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }

            String originalFileName = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFileName != null && originalFileName.contains(".")) {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }
            String uniqueFileName = java.util.UUID.randomUUID().toString() + fileExtension;
            java.nio.file.Path targetLocation = uploadPath.resolve(uniqueFileName);
            java.nio.file.Files.copy(file.getInputStream(), targetLocation, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            entity.setDocumentStoragePath(uniqueFileName);
            entity.setDocumentFileName(originalFileName);
            entity.setDocumentFileType(file.getContentType());
            entity.setDocumentFileSize(file.getSize());
            if (dtoHasDocumentType(entity)) {
                // Keep entity's value
            } else {
                entity.setDocumentType("SUPPORTING_DOC");
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("Could not store file: " + e.getMessage(), e);
        }
    }

    private boolean dtoHasDocumentType(BasicProfileChangeRequest entity) {
        return entity.getDocumentType() != null && !entity.getDocumentType().isBlank();
    }

    private void deleteFileIfExists(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get("uploads").resolve(fileName).normalize();
            java.nio.file.Files.deleteIfExists(filePath);
        } catch (java.io.IOException e) {
            System.err.println("Warning: could not delete file: " + e.getMessage());
        }
    }
}

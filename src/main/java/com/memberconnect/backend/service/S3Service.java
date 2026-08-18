package com.memberconnect.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class S3Service {

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

    @Autowired(required = false)
    private S3Client s3Client;

    @Value("${aws.bucketName:memberconnect-documents}")
    private String bucketName;

    private static final String FALLBACK_DIR = "uploads";

    public String uploadFile(MultipartFile file) throws IOException {
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

        try {
            if (s3Client != null) {
                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .contentType(file.getContentType())
                        .build();

                s3Client.putObject(
                        request,
                        RequestBody.fromBytes(file.getBytes())
                );
                logger.info("Uploaded file to AWS S3: {}", fileName);
                return fileName;
            }
        } catch (Exception e) {
            logger.warn("AWS S3 upload failed ({}), falling back to local storage.", e.getMessage());
        }

        // Fallback: Save file locally if S3 is unavailable or credentials missing
        try {
            Path uploadPath = Paths.get(FALLBACK_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path filePath = uploadPath.resolve(fileName);
            Files.write(filePath, file.getBytes());
            logger.info("Saved file locally as fallback: {}", filePath);
        } catch (Exception ex) {
            logger.error("Failed to save local fallback file: {}", ex.getMessage());
        }

        return fileName;
    }

    public void deleteFile(String fileName) {
        try {
            if (s3Client != null) {
                DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .build();

                s3Client.deleteObject(deleteObjectRequest);
            }
        } catch (Exception e) {
            logger.warn("Failed to delete file from AWS S3: {}", e.getMessage());
        }

        try {
            Path filePath = Paths.get(FALLBACK_DIR).resolve(fileName);
            Files.deleteIfExists(filePath);
        } catch (Exception ignored) {}
    }

    public byte[] downloadFile(String fileName) {
        try {
            if (s3Client != null) {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .build();

                return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
            }
        } catch (Exception e) {
            logger.warn("AWS S3 download failed ({}), trying local storage.", e.getMessage());
        }

        // Fallback: Read from local storage
        try {
            Path filePath = Paths.get(FALLBACK_DIR).resolve(fileName);
            if (Files.exists(filePath)) {
                return Files.readAllBytes(filePath);
            }
        } catch (Exception e) {
            logger.error("Failed to read fallback file locally: {}", e.getMessage());
        }

        return new byte[0];
    }

    /**
     * Reads the Content-Type stored with the S3 object at upload time.
     * Falls back to "application/octet-stream" if unavailable.
     */
    public String getFileContentType(String fileName) {
        try {
            software.amazon.awssdk.services.s3.model.HeadObjectResponse head =
                    s3Client.headObject(r -> r.bucket(bucketName).key(fileName));
            String ct = head.contentType();
            return (ct != null && !ct.isBlank()) ? ct : "application/octet-stream";
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }
}


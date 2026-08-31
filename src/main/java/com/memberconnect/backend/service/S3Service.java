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
import java.time.Year;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class S3Service {

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

    @Autowired(required = false)
    private S3Client s3Client;

    @Value("${aws.bucketName:memberconnect-documents}")
    private String bucketName;

    private static final String FALLBACK_DIR = "uploads";

    /** Anything outside this set is replaced with '-' when building a key segment. */
    private static final Pattern UNSAFE = Pattern.compile("[^a-z0-9.]+");


    public static String folder(String module, String... segments) {
        StringBuilder path = new StringBuilder(slug(module));
        path.append('/').append(Year.now().getValue());
        for (String segment : segments) {
            String cleaned = slug(segment);
            if (!cleaned.isEmpty()) {
                path.append('/').append(cleaned);
            }
        }
        return path.toString();
    }

    /** Lowercases and strips anything that would be awkward or unsafe in a key. */
    private static String slug(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = UNSAFE.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-");
        // A run of dots must never survive: "..' would climb a directory level.
        cleaned = cleaned.replaceAll("\\.{2,}", ".");
        cleaned = cleaned.replaceAll("-{2,}", "-").replaceAll("^[-.]+|[-.]+$", "");
        return cleaned;
    }

    // Helper method to get the safe file name
    private static String safeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }
        String base = originalFilename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        String cleaned = slug(base);
        return cleaned.isEmpty() ? "file" : cleaned;
    }

    // 
    public String uploadFile(MultipartFile file) throws IOException {
        return uploadFile(file, folder("misc"));
    }

    /**
     * Uploads into a folder, returning the full S3 key to store on the owning record.
     */
    public String uploadFile(MultipartFile file, String prefix) throws IOException {
        String cleanPrefix = (prefix == null || prefix.isBlank()) ? folder("misc") : prefix;
        String fileName = cleanPrefix + "/" + UUID.randomUUID() + "_" + safeFileName(file.getOriginalFilename());

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
            // fileName now contains '/' separators, so create the parent chain rather
            // than just the root upload directory.
            Path filePath = Paths.get(FALLBACK_DIR).resolve(fileName);
            Files.createDirectories(filePath.getParent());
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


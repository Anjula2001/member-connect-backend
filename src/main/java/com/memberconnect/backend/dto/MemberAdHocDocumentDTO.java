package com.memberconnect.backend.dto;

import java.time.LocalDateTime;

/** One ad-hoc document as the Add Documents screen and the profile Documents tab show it. */
public class MemberAdHocDocumentDTO {

    private Long id;
    private String memberId;
    private String fileName;
    private String fileType;
    private LocalDateTime uploadedAt;
    private String uploadedBy;

    public MemberAdHocDocumentDTO() {
    }

    public MemberAdHocDocumentDTO(Long id, String memberId, String fileName, String fileType,
            LocalDateTime uploadedAt, String uploadedBy) {
        this.id = id;
        this.memberId = memberId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.uploadedAt = uploadedAt;
        this.uploadedBy = uploadedBy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}

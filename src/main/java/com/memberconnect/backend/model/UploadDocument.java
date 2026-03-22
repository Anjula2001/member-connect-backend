package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "Upload_Document")
public class UploadDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String documentId;

    @Column(name = "FileName")
    private String fileName;

    @Column(name = "FileType")
    private String fileType;

    @Column(name = "UploadedAt")
    private LocalDateTime uploadedAt;
}
package com.memberconnect.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberDeathDocumentDTO {
    private Long id;
    private String recordNo;
    private String documentType;
    private String fileName;
    private String fileType;
    private String uploadedAt;
    private boolean mandatory;
}

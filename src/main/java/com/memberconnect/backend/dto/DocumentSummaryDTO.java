package com.memberconnect.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DocumentSummaryDTO {
    private int mandatoryDocumentCount;
    private int uploadedMandatoryDocumentCount;
    private int totalUploadedDocumentCount;
}

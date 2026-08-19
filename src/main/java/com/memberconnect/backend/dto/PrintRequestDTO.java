package com.memberconnect.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class PrintRequestDTO {
    /** Member table ids whose document is being marked as printed. */
    private List<Long> memberIds;
    /** When true, allow re-printing a document that already has a printed date. */
    private Boolean reprint;
}

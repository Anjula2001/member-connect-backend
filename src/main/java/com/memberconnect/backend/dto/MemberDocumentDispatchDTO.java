package com.memberconnect.backend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MemberDocumentDispatchDTO {
    private Long id;
    private String dispatchNo;
    private LocalDate dispatchDate;
    private String dispatchedBy;
    private LocalDateTime createdAt;
    private Integer memberCount;
    /** Populated only when a single dispatch is retrieved (for the Dispatch Report). */
    private List<MemberDTO> members;
}

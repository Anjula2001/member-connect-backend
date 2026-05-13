package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.TerminationReason;
import com.memberconnect.backend.enums.TerminationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberTerminationDTO {
    private Long id;
    private String terminationId;
    private Long memberId;
    private String memberName;
    private String memberId_Code;
    private TerminationReason terminationReason;
    private TerminationStatus terminationStatus;
    private LocalDate terminationDate;
    private LocalDate requestedDate;
    private LocalDate approvedDate;
    private LocalDate processedDate;
    private String remarks;
    private String approvedBy;
    private String processedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

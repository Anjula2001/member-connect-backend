package com.memberconnect.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditDTO {
    private Long id;
    private String moduleName;
    private Long referenceId;
    private String actionName;
    private String oldValue;
    private String newValue;
    private String remarks;
    /** Display name of the user who triggered the action. */
    private String actionBy;
    private LocalDateTime actionAt;
}

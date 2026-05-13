package com.memberconnect.backend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Small DTO used when the user marks a request as INCOMPLETE.
 * Only the reason is needed.
 */
@Getter
@Setter
public class MarkIncompleteDTO {
    private String reason;
}

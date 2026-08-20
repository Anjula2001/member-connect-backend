package com.memberconnect.backend.dto;

import lombok.Data;

/**
 * A Termination Reasons Master row as the maintenance screen sees it.
 *
 * Separate from {@link TerminationReasonDTO}, which is the trimmed dropdown shape
 * served to the MMT01 request form. That one carries id/code/name only; widening it
 * would put "active" and "displayOrder" into a payload the request form has no use
 * for, and would tie the two audiences' contracts together.
 */
@Data
public class TerminationReasonMasterDto {
    private Long id;
    private String code;
    private String name;
    private Boolean active;
    private Integer displayOrder;
}

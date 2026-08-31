package com.memberconnect.backend.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One page of the merged Board Approvals list, plus the totals for its footer. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalListPageDTO {

    private List<ApprovalListRowDTO> content;

    /** Zero-based, and not necessarily the page asked for. */
    private int page;

    private int size;

    /** Rows across both tables matching the filter. */
    private long totalElements;

    private int totalPages;
}

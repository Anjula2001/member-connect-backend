package com.memberconnect.backend.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One page of the New Member Registration List, plus the totals the screen needs to
 * render its footer and its "select all" checkbox without holding the other pages.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSearchPageDTO {

    /** The requested page only. */
    private List<MemberApplicationDTO> content;

    /** Zero-based, as requested. */
    private int page;

    private int size;

    /** Rows matching the filter across every page. */
    private long totalElements;

    private int totalPages;

    /**
     * How many of the matching rows are in a status the operator may tick.
     *
     * The select-all checkbox spans the whole result rather than the visible page, so
     * it needs a total to tell "all selected" from "some selected". Counting in the
     * database is what lets that survive pagination — the alternative is shipping every
     * matching row to the browser again, which is the cost this change removes.
     */
    private long selectableCount;
}

package com.memberconnect.backend.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One page of the Member Directory, plus the totals the screen's footer needs once
 * it no longer holds the other pages to count them.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberSearchPageDTO {

    /** The requested page only. */
    private List<MemberDTO> content;

    /** Zero-based, and not necessarily the page asked for — see MemberService. */
    private int page;

    private int size;

    /** Members matching the filter across every page. */
    private long totalElements;

    private int totalPages;
}

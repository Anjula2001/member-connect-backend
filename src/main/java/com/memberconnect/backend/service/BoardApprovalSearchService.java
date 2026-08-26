package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.memberconnect.backend.dto.ApprovalListPageDTO;
import com.memberconnect.backend.dto.ApprovalListRowDTO;
import com.memberconnect.backend.dto.BoardApprovalListDTO;
import com.memberconnect.backend.dto.TerminationApprovalListDTO;

/**
 * Serves the Board Approvals list, which is one table over two entities.
 *
 * board_approval_list holds membership, name-change and nominee-change lists;
 * termination approval lists are a separate entity with their own table and
 * controller. The screen shows them merged and ordered by when they were created.
 *
 * <h2>Why the merge is in memory</h2>
 *
 * The two tables have no common parent and no association, so there is no single
 * query to order across both — the same reason ProfileChangeSearchService merges its
 * five request types in memory. What is pushed down is the part that scales: the
 * board meeting date period is a SQL predicate on each table, and the item counts
 * come from grouped COUNT queries rather than from loading each list's contents.
 *
 * So the cost here is proportional to the number of lists MATCHING the filter, not
 * to the size of either table, and the browser receives one page instead of every
 * row with its full contents. Paging cannot be pushed into the database without a
 * native UNION query; that is a fair trade at this size and is recorded here so the
 * limit is known rather than discovered.
 */
@Service
public class BoardApprovalSearchService {

    /** Matches DEFAULT_PAGE_SIZE in the frontend's TablePagination control. */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** Guards against a caller asking for everything in one page. */
    private static final int MAX_PAGE_SIZE = 200;

    private final BoardApprovalListService boardApprovalListService;
    private final TerminationApprovalListService terminationApprovalListService;

    public BoardApprovalSearchService(
            BoardApprovalListService boardApprovalListService,
            TerminationApprovalListService terminationApprovalListService) {
        this.boardApprovalListService = boardApprovalListService;
        this.terminationApprovalListService = terminationApprovalListService;
    }

    public ApprovalListPageDTO search(LocalDate from, LocalDate to, Integer page, Integer size) {
        List<ApprovalListRowDTO> rows = new ArrayList<>();

        for (BoardApprovalListDTO dto : boardApprovalListService.getAllBoardApprovalLists(from, to)) {
            if (dto.getListId() == null) {
                continue;
            }
            rows.add(membershipRow(dto));
        }

        for (TerminationApprovalListDTO dto : terminationApprovalListService.getAllApprovalLists(from, to)) {
            if (dto.getListId() == null) {
                continue;
            }
            rows.add(new ApprovalListRowDTO(
                    "termination",
                    "termination",
                    dto.getListId(),
                    dto.getStatus(),
                    dto.getBoardMeetingId(),
                    dto.getBoardMeetingDate(),
                    dto.getCreatedAt(),
                    dto.getRequestCount() == null ? 0 : dto.getRequestCount()));
        }

        rows.sort(newestFirst());

        int pageSize = (size == null || size < 1) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int totalElements = rows.size();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        int pageNumber = (page == null || page < 0) ? 0 : page;
        // Narrowing the period can leave the requested page past the end of what is
        // left. Answer with the last page that does exist and report which one it was,
        // rather than sending an empty page the browser has to notice and ask about.
        if (pageNumber > 0 && pageNumber >= totalPages) {
            pageNumber = totalPages == 0 ? 0 : totalPages - 1;
        }

        int start = Math.min(pageNumber * pageSize, totalElements);
        int end = Math.min(start + pageSize, totalElements);

        return new ApprovalListPageDTO(
                new ArrayList<>(rows.subList(start, end)),
                pageNumber,
                pageSize,
                totalElements,
                totalPages);
    }

    /**
     * A membership list's kind and count, which the browser used to work out.
     *
     * applicationCount comes from the server's grouped count; applicationIds is only
     * populated when a single list is opened, so counting that here would read 0 for
     * every row in the listing.
     */
    private ApprovalListRowDTO membershipRow(BoardApprovalListDTO dto) {
        int names = dto.getNameChangeRequestIds() == null ? 0 : dto.getNameChangeRequestIds().size();
        int nominees = dto.getNomineeChangeRequestIds() == null ? 0 : dto.getNomineeChangeRequestIds().size();
        int applications = dto.getApplicationCount() != null
                ? dto.getApplicationCount()
                : (dto.getApplicationIds() == null ? 0 : dto.getApplicationIds().size());

        String content = names > 0 ? "name-change" : nominees > 0 ? "nominee-change" : "applications";
        int itemCount = names > 0 ? names : nominees > 0 ? nominees : applications;

        return new ApprovalListRowDTO(
                "membership",
                content,
                dto.getListId(),
                dto.getStatus(),
                dto.getBoardMeetingId(),
                dto.getBoardMeetingDate(),
                dto.getCreatedAt(),
                itemCount);
    }

    /**
     * Newest first, by creation time, falling back to the meeting date for a list
     * created before createdAt was recorded — the same ordering the browser applied.
     *
     * The listId tiebreaker is what makes paging safe: two lists created in the same
     * instant must have a stable order, or one can appear on two pages while another
     * is never shown.
     */
    private Comparator<ApprovalListRowDTO> newestFirst() {
        Comparator<ApprovalListRowDTO> byInstant = Comparator.comparing(
                BoardApprovalSearchService::orderingInstant,
                Comparator.nullsLast(Comparator.reverseOrder()));

        return byInstant.thenComparing(
                ApprovalListRowDTO::getListId,
                Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static LocalDateTime orderingInstant(ApprovalListRowDTO row) {
        if (row.getCreatedAt() != null) {
            return row.getCreatedAt();
        }
        return row.getBoardMeetingDate() == null ? null : row.getBoardMeetingDate().atStartOfDay();
    }
}

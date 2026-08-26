package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.ApprovalListPageDTO;
import com.memberconnect.backend.dto.BoardApprovalListDTO;
import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.dto.NameChangeRequestDTO;
import com.memberconnect.backend.dto.NommineChangeRequestDTO;
import com.memberconnect.backend.service.BoardApprovalListService;
import com.memberconnect.backend.service.BoardApprovalSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/board-approval-lists")
@CrossOrigin
// Board Approval Lists are a Head Office / Board Secretariat concern — District Office
// has no business creating, viewing, approving or deleting them.
@PreAuthorize("hasAnyRole('HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
public class BoardApprovalListController {

	@Autowired
	private BoardApprovalListService boardApprovalListService;

	@Autowired
	private BoardApprovalSearchService boardApprovalSearchService;

	@PostMapping("/createBoardApprovalList")
	public BoardApprovalListDTO createBoardApprovalList(@RequestBody BoardApprovalListDTO boardApprovalListDTO) {
		return boardApprovalListService.createBoardApprovalList(boardApprovalListDTO);
	}

	/**
	 * MR07: retrieve by "All" or a Board Meeting date period. Both bounds are optional
	 * and independent, so an open-ended period may pass just one.
	 */
	@GetMapping("/getAllBoardApprovalLists")
	public List<BoardApprovalListDTO> getAllBoardApprovalLists(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return boardApprovalListService.getAllBoardApprovalLists(from, to);
	}

	/**
	 * One page of the Board Approvals list, merged across both approval list tables.
	 *
	 * The screen shows membership and termination lists in a single table, and used to
	 * fetch both endpoints in full and merge them in the browser. Merging server-side
	 * is what makes a page meaningful: the order runs across both sources, so page 2
	 * of the merged result cannot be assembled from page 2 of each.
	 *
	 * Lives on this controller rather than a third one because the class-level roles
	 * are identical on both, and any caller of this screen already needs both.
	 *
	 * {@code page} is zero-based; {@code size} is capped server-side.
	 */
	@GetMapping("/combined/page")
	public ApprovalListPageDTO searchCombinedApprovalLists(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return boardApprovalSearchService.search(from, to, page, size);
	}

	@GetMapping("/getBoardApprovalListByListId/{listId}")
	public BoardApprovalListDTO getBoardApprovalListByListId(@PathVariable String listId) {
		return boardApprovalListService.getBoardApprovalListByListId(listId);
	}

	@GetMapping("/getApplicationsByListId/{listId}")
	public List<MemberApplicationDTO> getApplicationsByListId(@PathVariable String listId) {
		return boardApprovalListService.getApplicationsByListId(listId);
	}

	@GetMapping("/getNameChangeRequestsByListId/{listId}")
	public List<NameChangeRequestDTO> getNameChangeRequestsByListId(@PathVariable String listId) {
		return boardApprovalListService.getNameChangeRequestsByListId(listId);
	}

	@GetMapping("/getNomineeChangeRequestsByListId/{listId}")
	public List<NommineChangeRequestDTO> getNomineeChangeRequestsByListId(@PathVariable String listId) {
		return boardApprovalListService.getNomineeChangeRequestsByListId(listId);
	}

	// MMC12 / MMC25: recording what the board decided is Board Secretary work. Head
	// Office builds the list, prints it for the meeting and retrieves it afterwards -
	// it inherits the class-level rule for all of that - but the approve/reject
	// decision on a request routed through the board is not its to make.
	@PreAuthorize("hasAnyRole('BOARD_SECRETARY','SUPER_ADMIN')")
	@PatchMapping("/processBoardApprovalList/{listId}")
	public BoardApprovalListDTO processBoardApprovalList(
			@PathVariable String listId,
			@RequestBody BoardApprovalListDTO boardApprovalListDTO) {
		return boardApprovalListService.processBoardApprovalList(listId, boardApprovalListDTO);
	}

	// Delete is the "delete privilege" the spec calls out separately — Head Office does
	// not get it, only Board Secretary/Super Admin.
	@PreAuthorize("hasAnyRole('BOARD_SECRETARY','SUPER_ADMIN')")
	@DeleteMapping("/deleteBoardApprovalList/{listId}")
	public String deleteBoardApprovalList(@PathVariable String listId) {
		return boardApprovalListService.deleteBoardApprovalList(listId);
	}

    /**
     * Row count for the dashboard, so a counter does not have to download the rows.
     * Inherits the same authorization as the listing beside it.
     */
    @GetMapping("/count")
    public java.util.Map<String, Long> countBoardApprovalLists(
            @RequestParam(required = false) java.util.List<String> statuses) {
        return java.util.Map.of("count", boardApprovalListService.countByStatuses(statuses));
    }
}

package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.BoardApprovalListDTO;
import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.dto.NameChangeRequestDTO;
import com.memberconnect.backend.dto.NommineChangeRequestDTO;
import com.memberconnect.backend.service.BoardApprovalListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

	@PostMapping("/createBoardApprovalList")
	public BoardApprovalListDTO createBoardApprovalList(@RequestBody BoardApprovalListDTO boardApprovalListDTO) {
		return boardApprovalListService.createBoardApprovalList(boardApprovalListDTO);
	}

	@GetMapping("/getAllBoardApprovalLists")
	public List<BoardApprovalListDTO> getAllBoardApprovalLists() {
		return boardApprovalListService.getAllBoardApprovalLists();
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
}

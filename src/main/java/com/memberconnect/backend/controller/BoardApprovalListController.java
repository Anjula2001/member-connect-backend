package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.BoardApprovalListDTO;
import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.service.BoardApprovalListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/board-approval-lists")
@CrossOrigin
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

	@DeleteMapping("/deleteBoardApprovalList/{listId}")
	public String deleteBoardApprovalList(@PathVariable String listId) {
		return boardApprovalListService.deleteBoardApprovalList(listId);
	}
}

package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.BoardMeetingDTO;
import com.memberconnect.backend.service.BoardMeetingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/board-meetings")
@CrossOrigin
// Board Meetings are a Head Office / Board Secretariat concern — District Office has no
// business creating, viewing or deleting them.
@PreAuthorize("hasAnyRole('HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
public class BoardMeetingController {

    @Autowired
    private BoardMeetingService boardMeetingService;

    @PostMapping("/createBoardMeeting")
    public BoardMeetingDTO saveBoardMeeting(@RequestBody BoardMeetingDTO boardMeetingDTO) {
        return boardMeetingService.saveBoardMeeting(boardMeetingDTO);
    }

    @GetMapping("/getAllBoardMeetings")
    public List<BoardMeetingDTO> getAllBoardMeetings() {
        return boardMeetingService.getAllBoardMeetings();
    }

    @GetMapping("/getBoardMeetingByBoardMeetingId/{boardMeetingId}")
    public BoardMeetingDTO getBoardMeetingByBoardMeetingId(@PathVariable String boardMeetingId) {
        return boardMeetingService.getBoardMeetingByBoardMeetingId(boardMeetingId);
    }

    @PutMapping("/updateBoardMeeting/{id}")
    public BoardMeetingDTO updateBoardMeeting(@PathVariable Long id, @RequestBody BoardMeetingDTO boardMeetingDTO) {
        return boardMeetingService.updateBoardMeeting(id, boardMeetingDTO);
    }

    // Delete is the "delete privilege" the spec calls out separately — Head Office does
    // not get it, only Board Secretary/Super Admin.
    @PreAuthorize("hasAnyRole('BOARD_SECRETARY','SUPER_ADMIN')")
    @DeleteMapping("/deleteBoardMeeting/{id}")
    public String deleteBoardMeeting(@PathVariable Long id) {
        return boardMeetingService.deleteBoardMeeting(id);
    }

}

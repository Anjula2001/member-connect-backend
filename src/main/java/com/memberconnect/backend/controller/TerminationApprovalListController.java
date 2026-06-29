package com.memberconnect.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.TerminationApprovalListDTO;
import com.memberconnect.backend.dto.TerminationRequestResponseDTO;
import com.memberconnect.backend.service.TerminationApprovalListService;

@RestController
@RequestMapping("/api/termination-approval-lists")
@CrossOrigin(origins = "http://localhost:3000")
public class TerminationApprovalListController {

    private final TerminationApprovalListService terminationApprovalListService;

    public TerminationApprovalListController(
            TerminationApprovalListService terminationApprovalListService
    ) {
        this.terminationApprovalListService = terminationApprovalListService;
    }

    @PostMapping("/createTerminationApprovalList")
    public TerminationApprovalListDTO createTerminationApprovalList(
            @RequestBody TerminationApprovalListDTO dto
    ) {
        return terminationApprovalListService.createTerminationApprovalList(dto);
    }

    @GetMapping("/getAllTerminationApprovalLists")
    public List<TerminationApprovalListDTO> getAllTerminationApprovalLists() {
        return terminationApprovalListService.getAllTerminationApprovalLists();
    }

    @GetMapping("/getTerminationApprovalListByListId/{listId}")
    public TerminationApprovalListDTO getTerminationApprovalListByListId(@PathVariable String listId) {
        return terminationApprovalListService.getTerminationApprovalListByListId(listId);
    }

    @GetMapping("/getRequestsByListId/{listId}")
    public List<TerminationRequestResponseDTO> getRequestsByListId(@PathVariable String listId) {
        return terminationApprovalListService.getRequestsByListId(listId);
    }

    @PatchMapping("/processTerminationApprovalList/{listId}")
    public TerminationApprovalListDTO processTerminationApprovalList(
            @PathVariable String listId,
            @RequestBody TerminationApprovalListDTO dto
    ) {
        return terminationApprovalListService.processTerminationApprovalList(listId, dto);
    }

    @DeleteMapping("/deleteTerminationApprovalList/{listId}")
    public String deleteTerminationApprovalList(@PathVariable String listId) {
        return terminationApprovalListService.deleteTerminationApprovalList(listId);
    }
}

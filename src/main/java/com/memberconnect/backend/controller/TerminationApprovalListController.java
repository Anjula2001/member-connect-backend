package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.TerminationApprovalListDTO;
import com.memberconnect.backend.dto.TerminationRequestResponseDTO;
import com.memberconnect.backend.service.TerminationApprovalListService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/termination-approval-lists")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasAnyRole('HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')")
public class TerminationApprovalListController {

    private final TerminationApprovalListService approvalListService;

    public TerminationApprovalListController(TerminationApprovalListService approvalListService) {
        this.approvalListService = approvalListService;
    }

    @PostMapping("/create")
    public TerminationApprovalListDTO createApprovalList(@RequestBody TerminationApprovalListDTO dto) {
        return approvalListService.createApprovalList(dto);
    }

    @GetMapping
    public List<TerminationApprovalListDTO> getAllApprovalLists() {
        return approvalListService.getAllApprovalLists();
    }

    @GetMapping("/{listId}")
    public TerminationApprovalListDTO getApprovalListByListId(@PathVariable String listId) {
        return approvalListService.getApprovalListByListId(listId);
    }

    @GetMapping("/{listId}/requests")
    public List<TerminationRequestResponseDTO> getRequestsByListId(@PathVariable String listId) {
        return approvalListService.getRequestsByListId(listId);
    }

    @PatchMapping("/{listId}/process")
    public TerminationApprovalListDTO processApprovalList(
            @PathVariable String listId,
            @RequestBody TerminationApprovalListDTO dto
    ) {
        return approvalListService.processApprovalList(listId, dto);
    }

    @DeleteMapping("/{listId}")
    @PreAuthorize("hasAnyRole('BOARD_SECRETARY','SUPER_ADMIN')")
    public String deleteApprovalList(@PathVariable String listId) {
        return approvalListService.deleteApprovalList(listId);
    }
}

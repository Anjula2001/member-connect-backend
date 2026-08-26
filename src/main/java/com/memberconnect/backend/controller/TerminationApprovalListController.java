package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.TerminationApprovalListDTO;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import com.memberconnect.backend.dto.TerminationRequestResponseDTO;
import com.memberconnect.backend.service.TerminationApprovalListService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * Retrieve by "All" or a Board Meeting date period. Both bounds are optional and
     * independent, so an open-ended period may pass just one.
     */
    @GetMapping
    public List<TerminationApprovalListDTO> getAllApprovalLists(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return approvalListService.getAllApprovalLists(from, to);
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

    /**
     * Row count for the dashboard, so a counter does not have to download the rows.
     * Inherits the same authorization as the listing beside it.
     */
    @GetMapping("/count")
    public java.util.Map<String, Long> countApprovalLists(
            @RequestParam(required = false) java.util.List<String> statuses) {
        return java.util.Map.of("count", approvalListService.countByStatuses(statuses));
    }
}

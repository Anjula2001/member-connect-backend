package com.memberconnect.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.config.CurrentUserService;
import com.memberconnect.backend.dto.MemberRetirementRequestDTO;
import com.memberconnect.backend.dto.RetirementRequestResponseDTO;
import com.memberconnect.backend.enums.Permission;
import com.memberconnect.backend.enums.RetirementRequestStatus;
import com.memberconnect.backend.service.RetirementService;


@RestController
@RequestMapping("/api/retirement-requests")
@CrossOrigin(origins = "http://localhost:3000")
public class RetirementRequestController {

    private final RetirementService retirementService;
    private final CurrentUserService currentUserService;

    public RetirementRequestController(
            RetirementService retirementService,
            CurrentUserService currentUserService) {
        this.retirementService = retirementService;
        this.currentUserService = currentUserService;
    }

    // Get all retirement requests
    @PreAuthorize("hasAuthority('RET_REQUEST_VIEW')")
    @GetMapping
    public List<RetirementRequestResponseDTO> searchRequests(
            @RequestParam(required = false) List<String> locations,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String searchKey,
            @RequestParam(defaultValue = "requestedDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder
    ) {
        return retirementService.searchRequests(
                locations,
                statuses,
                fromDate,
                toDate,
                searchKey,
                sortBy,
                sortOrder
        );
    }

    // Create a new retirement request for a member
    @PreAuthorize("hasAuthority('RET_REQUEST_CREATE')")
    @PostMapping("/{memberId}")
    public RetirementRequestResponseDTO saveRetirementRequest(
            @PathVariable String memberId,
            @RequestBody MemberRetirementRequestDTO dto
    ) {
        return retirementService.saveRequest(memberId, dto);
    }

    // Get retirement requests by member
    @PreAuthorize("hasAuthority('RET_REQUEST_VIEW')")
    @GetMapping("/member/{memberId}")
    public List<RetirementRequestResponseDTO> getRetirementRequestsByMember(
            @PathVariable String memberId
    ) {
        return retirementService.getRequestsByMember(memberId);
    }

    // Submit retirement request
    @PreAuthorize("hasAuthority('RET_REQUEST_SUBMIT')")
    @PostMapping("/{requestNo}/submit")
    public RetirementRequestResponseDTO submitRequest(
            @PathVariable String requestNo
    ) {
        return retirementService.submitRequest(requestNo);
    }

    // Mark incomplete
    @PreAuthorize("hasAuthority('RET_REQUEST_INCOMPLETE')")
    @PutMapping("/{requestNo}/mark-incomplete")
    public RetirementRequestResponseDTO markIncomplete(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return retirementService.markIncomplete(requestNo, reason);
    }

    // Approve request
    @PreAuthorize("hasAuthority('RET_REQUEST_APPROVE')")
    @PutMapping("/{requestNo}/approve")
    public RetirementRequestResponseDTO approveRequest(
            @PathVariable String requestNo
    ) {
        return retirementService.approveRequest(requestNo);
    }

    // Send request to finance module
    @PreAuthorize("hasAuthority('RET_REQUEST_APPROVE')")
    @PostMapping("/{requestNo}/send-to-finance")
    public RetirementRequestResponseDTO sendToFinanceModule(
            @PathVariable String requestNo
    ) {
        return retirementService.sendToFinanceModule(requestNo);
    }

    // Reject request
    @PreAuthorize("hasAuthority('RET_REQUEST_APPROVE')")
    @PutMapping("/{requestNo}/reject")
    public RetirementRequestResponseDTO rejectRequest(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return retirementService.rejectRequest(requestNo, reason);
    }

    // Change retirement request status
    @PutMapping("/{requestNo}/status")
    public RetirementRequestResponseDTO changeRetirementRequestStatus(
            @PathVariable String requestNo,
            @RequestBody Map<String, String> body
    ) {
        String status = body.get("status");

        String currentStatus = retirementService.getRequestByRequestNo(requestNo).getStatus();
        currentUserService.require(requiredPermissionForStatusChange(currentStatus, status));

        return retirementService.changeRequestStatus(requestNo, status);
    }

    // Determine the required permission for changing the status of a retirement request based on its current and requested status.
    private Permission requiredPermissionForStatusChange(String currentStatus, String requestedStatus) {
        if (requestedStatus == null) {
            return Permission.RET_REQUEST_EDIT;
        }

        if (RetirementRequestStatus.INACTIVE.name().equalsIgnoreCase(requestedStatus)) {
            return Permission.RET_REQUEST_SET_INACTIVE;
        }

        boolean isReturningToNew =
                RetirementRequestStatus.NEW.name().equalsIgnoreCase(requestedStatus)
                        && (RetirementRequestStatus.SUBMITTED_FOR_APPROVAL.name().equalsIgnoreCase(currentStatus)
                                || RetirementRequestStatus.REJECTED.name().equalsIgnoreCase(currentStatus)
                                || RetirementRequestStatus.INACTIVE.name().equalsIgnoreCase(currentStatus));
        if (isReturningToNew) {
            return Permission.RET_REQUEST_RETURN_TO_NEW;
        }

        return Permission.RET_REQUEST_EDIT;
    }

    // Get retirement request by ID
    @PreAuthorize("hasAuthority('RET_REQUEST_VIEW')")
    @GetMapping("/request/{id}")
    public RetirementRequestResponseDTO getRetirementRequestById(
            @PathVariable String id
    ) {
        return retirementService.getRequestByRequestNo(id);
    }

    // Update retirement request
    @PreAuthorize("hasAuthority('RET_REQUEST_EDIT')")
    @PutMapping("/{requestNo}")
    public RetirementRequestResponseDTO updateRetirementRequest(
            @PathVariable String requestNo,
            @RequestBody MemberRetirementRequestDTO dto
    ) {
        return retirementService.updateRequest(requestNo, dto);
    }

}

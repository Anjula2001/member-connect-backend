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

/**
 * Authorization note: the @PreAuthorize annotations sit on the controller rather than
 * the service, matching Grade5ScholarshipController. Spring rejects the call before the
 * method body runs, so GlobalExceptionHandler returns a proper 403 instead of the 400
 * that a denial raised deeper down would be downgraded to.
 *
 * MMT16 is the reason this exists: without it any authenticated user can approve a
 * retirement request, including the District Office clerk who raised it.
 */
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

    /**
     * Save retirement request.
     *
     * Guarded by RET_REQUEST_CREATE, but note that saveRequest doubles as an edit path:
     * when the member already has a non-Inactive request it updates that record instead
     * of creating one. A role granted RET_REQUEST_CREATE without RET_REQUEST_EDIT would
     * therefore be able to edit through here. No role in RolePermissions holds one
     * without the other, so nothing is exposed today — but any future grant that splits
     * them needs to add RET_REQUEST_EDIT to this check.
     */
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

    /**
     * MMT17 — send one approved retirement to the Finance Module and complete the
     * member's retirement.
     *
     * Guarded by RET_REQUEST_APPROVE rather than a right of its own: the retirement
     * approver is the office that follows the request through, and there is no
     * separate Finance actor in MMT12-MMT17 the way MMS20 has one for Grade 5.
     */
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

    /**
     * Change retirement request status (view mode).
     *
     * The right required depends on the target status, not on the endpoint, so this
     * cannot be a single @PreAuthorize.
     */
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

    /**
     * Maps a status change to the right needed to perform it.
     *
     * Two families of move are privileged above ordinary edit rights:
     *   -> INACTIVE                  SRS 3.2.4 qualifies this with "the user needs
     *                                Inactive rights", so it is not everyday editing.
     *   SUBMITTED/REJECTED/INACTIVE  Pulling a request back out of approval is qualified
     *   -> NEW                       by 3.2.1 with "if the logged in user has the rights
     *                                to change the status"; reopening a Rejected or
     *                                Inactive request overturns a closed decision.
     *
     * INCOMPLETE -> NEW stays on ordinary edit rights: that is the normal "fix the
     * request and carry on" path the District Office is expected to walk.
     *
     * An unrecognised status falls through to RET_REQUEST_EDIT and is then rejected by
     * the service's own transition matrix, so a bad payload cannot pick a weaker right.
     */
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

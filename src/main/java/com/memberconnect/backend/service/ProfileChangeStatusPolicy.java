package com.memberconnect.backend.service;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.model.User;

/**
 * The single place the Member Profile Change status rules are enforced.
 *
 * Requirement 02 constrains these requests far more tightly than the current
 * screens do. Every one of the four "Viewing an existing ..." functions (MMC03,
 * MMC07, MMC16, MMC20) states the same two rules, and every "Creating a ..."
 * function states the third:
 *
 *   1. From View Mode the only status changes permitted are
 *          Submitted for Approval -> Inactive
 *          Rejected               -> Inactive
 *      and both require Inactive rights.
 *   2. Every other field is locked in View Mode.
 *   3. "Once submitted, the user cannot edit the record."
 *
 * The screens instead offered a free dropdown of every status to every user, and
 * the list kept Edit and Delete enabled on submitted rows. Centralising the rules
 * here means the four services cannot drift apart on them again.
 */
@Service
public class ProfileChangeStatusPolicy {

    /**
     * Mirrors INACTIVE_RIGHTS_ROLES in the frontend's lib/permissions.ts. The SRS
     * calls this out as a distinct right rather than a role, so it is kept as its
     * own set instead of being folded into a general "can approve" check.
     */
    private static final Set<Role> INACTIVE_RIGHTS_ROLES =
            EnumSet.of(Role.SUPER_ADMIN, Role.HEAD_OFFICE, Role.BOARD_SECRETARY);

    /** Statuses a request can be moved to Inactive from. */
    private static final Set<ApplicationStatus> INACTIVATABLE =
            EnumSet.of(ApplicationStatus.SUBMITTED_FOR_APPROVAL, ApplicationStatus.REJECTED);

    /** Statuses an approver may record a decision against. */
    private static final Set<ApplicationStatus> DECIDABLE =
            EnumSet.of(ApplicationStatus.SUBMITTED_FOR_APPROVAL, ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST);

    /**
     * Statuses that may be pulled into a Name or Nominee Change Approval List.
     * MMC08 and MMC21: "Only the Name Changes and Nominee Changes Type Requests with
     * 'Submitted for Approval' and 'Rejected' statuses can be selected".
     */
    private static final Set<ApplicationStatus> LISTABLE =
            EnumSet.of(ApplicationStatus.SUBMITTED_FOR_APPROVAL, ApplicationStatus.REJECTED);

    /**
     * A request may only be edited before it is submitted. A null status is a record
     * that has never been through submit, which is the draft case.
     */
    public void assertEditable(ApplicationStatus current) {
        if (current != null && current != ApplicationStatus.NEW) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This request has already been submitted and can no longer be edited."
            );
        }
    }

    /**
     * A manual status change made from View Mode. Anything outside the two permitted
     * transitions is refused, as is a caller without Inactive rights.
     */
    public void assertManualStatusChange(ApplicationStatus current, ApplicationStatus target) {
        if (target != ApplicationStatus.INACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The only status change available from this screen is Inactive."
            );
        }

        if (current == null || !INACTIVATABLE.contains(current)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a request that is Submitted for Approval or Rejected can be made Inactive."
            );
        }

        if (!currentUserHasInactiveRights()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have Inactive rights."
            );
        }
    }

    /** An approver may only decide a request that is actually awaiting a decision. */
    public void assertDecidable(ApplicationStatus current) {
        if (current == null || !DECIDABLE.contains(current)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This request is not awaiting approval."
            );
        }
    }

    /** MMC08 / MMC21 - guards which rows may be pulled into an approval list. */
    public void assertListable(ApplicationStatus current, String requestNo) {
        if (current == null || !LISTABLE.contains(current)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Request " + requestNo + " cannot be added to an approval list: only requests that are "
                            + "Submitted for Approval or Rejected are eligible."
            );
        }
    }

    /** A reject decision must carry a reason (MMC04, MMC12, MMC17, MMC25). */
    public String requireRejectReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A reason is required when rejecting a request."
            );
        }
        return reason.trim();
    }

    public boolean currentUserHasInactiveRights() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof User user
                && INACTIVE_RIGHTS_ROLES.contains(user.getRole());
    }

    /** The username recorded against a decision, or "System" outside a request. */
    public String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getUsername();
        }
        return "System";
    }
}

package com.memberconnect.backend.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.memberconnect.backend.enums.ApplicationStatus;

/**
 * The status moves a Member Registration application may make through the
 * user-facing paths - the Submit button (MR01) and the Status Override field on the
 * edit screen (MR04).
 *
 * <h2>Why this exists</h2>
 *
 * MemberApplicationService accepted any target status on PATCH /{id}/status and on
 * the edit save, gated only by the "Inactive rights" check. The Status Override
 * dropdown offered all five statuses unconditionally, so a user could put an
 * application into states the spec reserves for the board flow:
 *
 * <ul>
 * <li>ADDED_TO_BOARD_APPROVAL_LIST is set ONLY by MR06, when the application is
 * genuinely attached to a Board Approval List. Set by hand it produces a record
 * that claims to be on a list which holds no such row - and which MR08's rollback
 * can therefore never restore, because there is no list to delete.</li>
 * <li>REJECTED is set ONLY by MR10, together with the rejection reason the spec
 * makes mandatory. Set by hand it leaves a rejection nobody can explain.</li>
 * <li>APPROVED is set ONLY by MR10, in the same step that creates the Member. Set
 * by hand it hides the application from the registration list - which excludes
 * APPROVED by spec - without any member ever existing.</li>
 * <li>SUBMITTED_FOR_APPROVAL by hand skips the mandatory-field, NIC-duplicate and
 * age validation that the Submit button runs.</li>
 * </ul>
 *
 * <h2>What is allowed</h2>
 *
 * MR01 gives NEW to SUBMITTED_FOR_APPROVAL (Submit). MR04 gives the override table:
 *
 * <pre>
 *   New                    -&gt; Inactive*
 *   Submitted for Approval -&gt; New, Inactive*
 *   Rejected               -&gt; New, Inactive*
 *   Inactive               -&gt; New
 *
 *   * needs "Inactive rights", checked separately in MemberApplicationService
 * </pre>
 *
 * ADDED_TO_BOARD_APPROVAL_LIST and APPROVED have no outgoing row at all: once the
 * board holds an application, only the board flow moves it.
 *
 * <h2>What this does not govern</h2>
 *
 * BoardApprovalListService writes status straight onto the entity for MR06, MR08
 * and MR10. Those are the board transitions themselves rather than a user
 * overriding a status, so they do not pass through here - and must not, or
 * attaching a list to a meeting would refuse its own status change.
 */
public final class ApplicationStatusPolicy {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TARGETS =
            new EnumMap<>(ApplicationStatus.class);

    static {
        ALLOWED_TARGETS.put(ApplicationStatus.NEW,
                EnumSet.of(ApplicationStatus.SUBMITTED_FOR_APPROVAL, ApplicationStatus.INACTIVE));
        ALLOWED_TARGETS.put(ApplicationStatus.SUBMITTED_FOR_APPROVAL,
                EnumSet.of(ApplicationStatus.NEW, ApplicationStatus.INACTIVE));
        ALLOWED_TARGETS.put(ApplicationStatus.REJECTED,
                EnumSet.of(ApplicationStatus.NEW, ApplicationStatus.INACTIVE));
        ALLOWED_TARGETS.put(ApplicationStatus.INACTIVE,
                EnumSet.of(ApplicationStatus.NEW));

        // MR10's board decision. It reaches the server as a partial update from the
        // Board Approvals screen (BoardApprovalListService processes the LIST, but the
        // per-application Approve/Reject is sent as PATCH status), so it passes through
        // this policy and has to be permitted here.
        //
        // Requiring the application to be ADDED_TO_BOARD_APPROVAL_LIST first is what
        // keeps this from reopening the hole: Approved and Rejected stay unreachable
        // from New, Submitted for Approval, Rejected and Inactive, so an application
        // can only be decided after a real board list has actually claimed it.
        ALLOWED_TARGETS.put(ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST,
                EnumSet.of(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED));

        // Terminal: the application became a Member.
        ALLOWED_TARGETS.put(ApplicationStatus.APPROVED,
                EnumSet.noneOf(ApplicationStatus.class));
    }

    private ApplicationStatusPolicy() {
    }

    /**
     * Normalises the statuses that mean "not started yet". PENDING is never written
     * by any member-application path, and a null status only occurs on rows created
     * before saveMemberApplication defaulted it; both read as NEW here so a legacy
     * record is still editable rather than frozen.
     */
    private static ApplicationStatus normalise(ApplicationStatus status) {
        return (status == null || status == ApplicationStatus.PENDING)
                ? ApplicationStatus.NEW
                : status;
    }

    /** The statuses a user may move {@code from} to. Drives the override dropdown. */
    public static Set<ApplicationStatus> allowedTargets(ApplicationStatus from) {
        return ALLOWED_TARGETS.getOrDefault(normalise(from), EnumSet.noneOf(ApplicationStatus.class));
    }

    /**
     * @throws ResponseStatusException 409 when the move is not one the spec allows.
     */
    public static void checkTransition(ApplicationStatus from, ApplicationStatus to) {
        if (to == null) {
            return;
        }

        ApplicationStatus current = normalise(from);

        // Re-saving the status it already has is what an ordinary edit does - the form
        // posts the loaded status back alongside every other field. That is not a
        // transition, and rejecting it would block editing an application's details.
        if (current == to) {
            return;
        }

        Set<ApplicationStatus> targets = allowedTargets(current);
        if (!targets.contains(to)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An application with status " + current + " cannot be changed to " + to + ". "
                            + (targets.isEmpty()
                                    ? "This application is held by the board approval flow and its status "
                                            + "can only be changed by processing or deleting that list."
                                    : "Allowed: " + targets + "."));
        }
    }
}

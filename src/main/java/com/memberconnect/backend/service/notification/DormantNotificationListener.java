package com.memberconnect.backend.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.memberconnect.backend.event.DormantInactivationApprovedEvent;
import com.memberconnect.backend.event.DormantSelectedEvent;
import com.memberconnect.backend.service.NotificationService;

/**
 * Turns committed dormant status changes into member notifications: flagged as
 * dormant by the identification process (MMD10), and inactivated once the Board
 * has approved it (MMD17, SRS 4.2.6).
 *
 * AFTER_COMMIT is the whole point of this class. DormantMembershipService is
 * annotated @Transactional at class level, so processApprovalList() runs inside a
 * transaction; firing the notification inline would hold that transaction open
 * across a network call and, worse, would let an SMS provider failure roll back a
 * board decision. Binding to AFTER_COMMIT guarantees the opposite of both: the
 * status is already durable before anything is sent, and if the transaction rolls
 * back this listener never runs, so no member is told about a change that did not
 * happen.
 *
 * That matters more here than elsewhere, because MMD17 is a bulk operation - a
 * single meeting may inactivate two hundred members, and one unreachable gateway
 * must not undo the lot.
 */
@Component
public class DormantNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(DormantNotificationListener.class);

    private final NotificationService notificationService;

    public DormantNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** MMD10 - flagged as dormant, and still reversible by the member. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDormantSelected(DormantSelectedEvent event) {
        try {
            notificationService.notifySelectedForDormant(
                    event.memberId(),
                    event.dormantPeriodMonths()
            );
        } catch (Exception e) {
            // NotificationService already isolates each channel, so reaching this
            // is unexpected. It is caught anyway: an exception thrown from an
            // after-commit callback cannot undo the commit, it only produces a
            // confusing failure on an operation that has already succeeded.
            log.error(
                    "Dormant selection notification failed unexpectedly. memberId={}, cause={}",
                    event.memberId(), e.toString(), e
            );
        }
    }

    /** MMD17 / SRS 4.2.6 - the Board approved and the member is now inactive. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDormantInactivationApproved(DormantInactivationApprovedEvent event) {
        try {
            notificationService.notifyMembershipInactivatedDormant(
                    event.memberId(),
                    event.listId()
            );
        } catch (Exception e) {
            log.error(
                    "Dormant inactivation notification failed unexpectedly. "
                            + "memberId={}, listId={}, cause={}",
                    event.memberId(), event.listId(), e.toString(), e
            );
        }
    }
}

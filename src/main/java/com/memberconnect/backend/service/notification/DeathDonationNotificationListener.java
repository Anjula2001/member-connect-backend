package com.memberconnect.backend.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.memberconnect.backend.event.DeathDonationApprovedEvent;
import com.memberconnect.backend.event.DeathDonationMarkedIncompleteEvent;
import com.memberconnect.backend.event.DeathDonationRejectedEvent;
import com.memberconnect.backend.service.NotificationService;

/**
 * Delivers the Death Donation notifications (SRS Requirement 05, section 2).
 *
 * Every handler runs AFTER_COMMIT: a donation decision is a business fact the
 * moment it is committed, and a notification that cannot be delivered must not
 * roll it back. Failures are logged, never rethrown - rethrowing from an
 * after-commit hook cannot undo the commit anyway, it would only lose the stack
 * trace.
 *
 * Unlike MemberDeathNotificationListener, approval DOES have a handler here.
 * The donation SRS notifies on the approval decision itself (p.5, "The Member
 * will receive an SMS and Email mentioning that the Death Donation Request has
 * been approved"), not on a later Finance completion.
 */
@Component
public class DeathDonationNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(DeathDonationNotificationListener.class);

    private final NotificationService notificationService;

    public DeathDonationNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeathDonationMarkedIncomplete(DeathDonationMarkedIncompleteEvent event) {
        try {
            notificationService.notifyDeathDonationMarkedIncomplete(
                    event.memberId(), event.requestNo(), event.reason());
        } catch (Exception e) {
            log.error(
                    "Death donation incomplete notification failed unexpectedly. "
                            + "memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeathDonationRejected(DeathDonationRejectedEvent event) {
        try {
            notificationService.notifyDeathDonationRejected(
                    event.memberId(), event.requestNo(), event.reason(), event.level());
        } catch (Exception e) {
            log.error(
                    "Death donation rejection notification failed unexpectedly. "
                            + "memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeathDonationApproved(DeathDonationApprovedEvent event) {
        try {
            notificationService.notifyDeathDonationApproved(
                    event.memberId(), event.requestNo(), event.level());
        } catch (Exception e) {
            log.error(
                    "Death donation approval notification failed unexpectedly. "
                            + "memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }
}

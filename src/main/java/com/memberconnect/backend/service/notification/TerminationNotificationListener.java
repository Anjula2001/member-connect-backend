package com.memberconnect.backend.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.memberconnect.backend.event.TerminationCompletedEvent;
import com.memberconnect.backend.event.TerminationMarkedIncompleteEvent;
import com.memberconnect.backend.event.TerminationRejectedEvent;
import com.memberconnect.backend.service.NotificationService;

/**
 * Turns committed termination status changes into member notifications: marked
 * INCOMPLETE (MMT01), rejected by the board (MMT09), and terminated once Finance
 * has closed the accounts (MMT11).
 *
 * AFTER_COMMIT is the whole point of this class. TerminationService is annotated
 * @Transactional at class level, so markIncomplete() runs inside a transaction; firing
 * the notification inline would hold that transaction open across a network call and,
 * worse, would let a provider failure roll back the status change. Binding to
 * AFTER_COMMIT guarantees the opposite of both: the INCOMPLETE status is already
 * durable before anything is sent, and if the transaction rolls back this listener
 * never runs at all, so no member is told about a change that did not happen.
 */
@Component
public class TerminationNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(TerminationNotificationListener.class);

    private final NotificationService notificationService;

    public TerminationNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTerminationMarkedIncomplete(TerminationMarkedIncompleteEvent event) {
        try {
            notificationService.notifyTerminationMarkedIncomplete(
                    event.memberId(),
                    event.requestNo(),
                    event.reason()
            );
        } catch (Exception e) {
            // NotificationService already isolates each channel, so reaching this
            // is unexpected. It is caught anyway: an exception thrown from an
            // after-commit callback cannot undo the commit, it only produces a
            // confusing failure on an operation that has already succeeded.
            log.error(
                    "Termination incomplete notification failed unexpectedly. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }

    /** MMT09 - the board rejected the request and the member is Active again. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTerminationRejected(TerminationRejectedEvent event) {
        try {
            notificationService.notifyTerminationRejected(
                    event.memberId(),
                    event.requestNo(),
                    event.reason()
            );
        } catch (Exception e) {
            log.error(
                    "Termination rejected notification failed unexpectedly. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }

    /** MMT11 - Finance has closed the accounts and the membership is now over. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTerminationCompleted(TerminationCompletedEvent event) {
        try {
            notificationService.notifyMembershipTerminated(
                    event.memberId(),
                    event.requestNo()
            );
        } catch (Exception e) {
            log.error(
                    "Membership terminated notification failed unexpectedly. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }
}

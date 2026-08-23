package com.memberconnect.backend.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.memberconnect.backend.event.MemberRetiredEvent;
import com.memberconnect.backend.event.RetirementMarkedIncompleteEvent;
import com.memberconnect.backend.event.RetirementRejectedEvent;
import com.memberconnect.backend.service.NotificationService;

/**
 * Turns committed retirement status changes into member notifications: marked
 * INCOMPLETE (MMT14), where the member has to act before the request can move on,
 * rejected (MMT16), where the request is over and the membership stays active, and
 * retired (MMT17), where Finance has settled the accounts and the membership has ended.
 *
 * AFTER_COMMIT for the same reason as TerminationNotificationListener: the INCOMPLETE
 * status is durable before anything is sent, and a rolled-back transaction never
 * reaches this listener, so no member is told about a change that did not happen.
 *
 * Note that RetirementService.markIncomplete carries @Transactional purely so this
 * binding has a transaction to hang off - without one the event would be dropped
 * rather than delivered.
 */
@Component
public class RetirementNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(RetirementNotificationListener.class);

    private final NotificationService notificationService;

    public RetirementNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRetirementMarkedIncomplete(RetirementMarkedIncompleteEvent event) {
        try {
            notificationService.notifyRetirementMarkedIncomplete(
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
                    "Retirement incomplete notification failed unexpectedly. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }

    /** MMT16 - the request was rejected and the member is Active again. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRetirementRejected(RetirementRejectedEvent event) {
        try {
            notificationService.notifyRetirementRejected(
                    event.memberId(),
                    event.requestNo(),
                    event.reason()
            );
        } catch (Exception e) {
            log.error(
                    "Retirement rejected notification failed unexpectedly. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }

    /** MMT17 - Finance has settled the accounts and the membership has ended on retirement. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberRetired(MemberRetiredEvent event) {
        try {
            notificationService.notifyMemberRetired(
                    event.memberId(),
                    event.requestNo()
            );
        } catch (Exception e) {
            log.error(
                    "Member retired notification failed unexpectedly. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }
}

package com.memberconnect.backend.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.memberconnect.backend.event.MemberDeathCompletedEvent;
import com.memberconnect.backend.event.MemberDeathMarkedIncompleteEvent;
import com.memberconnect.backend.event.MemberDeathRejectedEvent;
import com.memberconnect.backend.service.NotificationService;

/**
 * Delivers the Record Member Death notifications (SRS section 4).
 *
 * Every handler runs AFTER_COMMIT: a member death decision is a business fact
 * the moment it is committed, and a notification that cannot be delivered must
 * not roll it back. Failures are therefore logged, never rethrown - rethrowing
 * from an after-commit hook cannot undo the commit anyway, it would only lose
 * the stack trace.
 *
 * Approval deliberately has no handler. The SRS notifies the nominee when the
 * membership actually ends, which is the Finance completion (MMT25), not the
 * board decision that precedes it - see MemberDeathCompletedEvent below.
 */
@Component
public class MemberDeathNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(MemberDeathNotificationListener.class);

    private final NotificationService notificationService;

    public MemberDeathNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberDeathMarkedIncomplete(MemberDeathMarkedIncompleteEvent event) {
        try {
            notificationService.notifyMemberDeathMarkedIncomplete(
                    event.memberId(), event.recordNo(), event.reason());
        } catch (Exception e) {
            log.error(
                    "Member death incomplete notification failed unexpectedly. memberId={}, recordNo={}, cause={}",
                    event.memberId(), event.recordNo(), e.toString(), e
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberDeathRejected(MemberDeathRejectedEvent event) {
        try {
            notificationService.notifyMemberDeathRejected(
                    event.memberId(), event.recordNo(), event.reason(), event.level());
        } catch (Exception e) {
            log.error(
                    "Member death rejection notification failed unexpectedly. memberId={}, recordNo={}, cause={}",
                    event.memberId(), event.recordNo(), e.toString(), e
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberDeathCompleted(MemberDeathCompletedEvent event) {
        try {
            notificationService.notifyMemberDeceased(event.memberId(), event.recordNo());
        } catch (Exception e) {
            log.error(
                    "Member deceased notification failed unexpectedly. memberId={}, recordNo={}, cause={}",
                    event.memberId(), event.recordNo(), e.toString(), e
            );
        }
    }
}

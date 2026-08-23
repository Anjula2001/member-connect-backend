package com.memberconnect.backend.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.memberconnect.backend.event.Grade5ApprovedEvent;
import com.memberconnect.backend.event.Grade5MarkedIncompleteEvent;
import com.memberconnect.backend.event.Grade5RejectedEvent;
import com.memberconnect.backend.service.NotificationService;

/**
 * Turns committed Grade 5 scholarship status changes into member notifications: marked
 * INCOMPLETE (MMS04), where the member has to supply something before the request can
 * be submitted, and the Board's decision at MMS11 / MMS18 - approved, with disbursement
 * being arranged, or rejected with the reason.
 *
 * AFTER_COMMIT for the same reason as the other notification listeners here: the
 * INCOMPLETE status is durable before anything is sent, and a rolled-back transaction
 * never reaches this listener, so no member is told about a change that did not happen.
 *
 * Grade5ScholarshipService.markIncomplete carries @Transactional purely so this binding
 * has a transaction to hang off - without one Spring discards the event unsent.
 */
@Component
public class Grade5NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(Grade5NotificationListener.class);

    private final NotificationService notificationService;

    public Grade5NotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGrade5MarkedIncomplete(Grade5MarkedIncompleteEvent event) {
        try {
            notificationService.notifyGrade5MarkedIncomplete(
                    event.memberId(),
                    event.requestNo(),
                    event.studentName(),
                    event.reason()
            );
        } catch (Exception e) {
            // NotificationService already isolates each channel, so reaching this is
            // unexpected. It is caught anyway: an exception thrown from an after-commit
            // callback cannot undo the commit, it only produces a confusing failure on
            // an operation that has already succeeded.
            log.error(
                    "Grade 5 incomplete notification failed unexpectedly. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }

    /** MMS11 / MMS18 - the Board rejected the request. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGrade5Rejected(Grade5RejectedEvent event) {
        try {
            notificationService.notifyGrade5Rejected(
                    event.memberId(),
                    event.requestNo(),
                    event.studentName(),
                    event.reason()
            );
        } catch (Exception e) {
            log.error(
                    "Grade 5 rejected notification failed unexpectedly. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }

    /** MMS11 / MMS18 - the Board approved the request and disbursement is being arranged. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGrade5Approved(Grade5ApprovedEvent event) {
        try {
            notificationService.notifyGrade5Approved(
                    event.memberId(),
                    event.requestNo(),
                    event.studentName()
            );
        } catch (Exception e) {
            log.error(
                    "Grade 5 approved notification failed unexpectedly. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }
}

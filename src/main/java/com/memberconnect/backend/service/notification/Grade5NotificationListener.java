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
           
            log.error(
                    "Grade 5 incomplete notification failed unexpectedly. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }

    /** the Board rejected the request. */
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

    /** Board approved the request and disbursement is being arranged. */
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

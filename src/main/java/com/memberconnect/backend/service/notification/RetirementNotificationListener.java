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
            
            log.error(
                    "Retirement incomplete notification failed unexpectedly. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }

    /** the request was rejected and the member is Active again. */
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

    /** Finance has settled the accounts and the membership has ended on retirement. */
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

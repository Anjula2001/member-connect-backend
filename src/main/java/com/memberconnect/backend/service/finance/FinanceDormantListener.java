package com.memberconnect.backend.service.finance;

import com.memberconnect.backend.event.DormantInactivationApprovedEvent;
import com.memberconnect.backend.service.DormantMembershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands a board-approved dormant inactivation to the Finance Module once the
 * decision has committed (SRS 4.2.7).
 *
 * AFTER_COMMIT for the same reasons as DormantNotificationListener, and one more
 * that matters especially here: the approval runs inside processApprovalList,
 * which may be inactivating two hundred members at once. Calling Finance inline
 * would hold that transaction open across two hundred network round trips, and a
 * single unreachable call would roll back an entire board meeting.
 */
@Component
public class FinanceDormantListener {

    private static final Logger log = LoggerFactory.getLogger(FinanceDormantListener.class);

    private final DormantMembershipService dormantService;
    private final FinanceDormantClient financeClient;

    public FinanceDormantListener(
            DormantMembershipService dormantService,
            FinanceDormantClient financeClient
    ) {
        this.dormantService = dormantService;
        this.financeClient = financeClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDormantInactivationApproved(DormantInactivationApprovedEvent event) {
        try {
            financeClient.sendDormantInactivated(
                    dormantService.buildFinanceHandoff(event.memberId(), event.listId())
            );
        } catch (Exception e) {
            // FinanceDormantClient already swallows transport failures, so
            // reaching this means the payload could not even be assembled.
            // Caught anyway: throwing from an after-commit callback cannot undo
            // the commit, it only fails an operation that already succeeded.
            log.error(
                    "Finance dormant handoff could not be prepared. memberId={}, listId={}, cause={}",
                    event.memberId(), event.listId(), e.toString(), e
            );
        }
    }
}

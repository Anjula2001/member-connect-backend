package com.memberconnect.backend.service.finance;

import com.memberconnect.backend.event.TerminationApprovedEvent;
import com.memberconnect.backend.service.TerminationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands a board-approved termination to the Finance Module once the approval has
 * committed (SRS MMT11).
 *
 * AFTER_COMMIT for the same reasons as TerminationNotificationListener, and one
 * more that matters here: the approval runs inside processApprovalList, which
 * may be approving a dozen members at once. Calling Finance inline would hold
 * that transaction open across a dozen network round trips, and a single
 * unreachable call would roll back an entire board meeting.
 */
@Component
public class FinanceTerminationListener {

    private static final Logger log = LoggerFactory.getLogger(FinanceTerminationListener.class);

    private final TerminationService terminationService;
    private final FinanceTerminationClient financeClient;

    public FinanceTerminationListener(
            TerminationService terminationService,
            FinanceTerminationClient financeClient
    ) {
        this.terminationService = terminationService;
        this.financeClient = financeClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTerminationApproved(TerminationApprovedEvent event) {
        try {
            financeClient.sendTerminationApproved(
                    terminationService.buildFinanceHandoff(event.requestNo())
            );
        } catch (Exception e) {
            // FinanceTerminationClient already swallows transport failures, so
            // reaching this means the payload could not even be assembled.
            // Caught anyway: throwing from an after-commit callback cannot undo
            // the commit, it only fails an operation that already succeeded.
            log.error(
                    "Finance termination handoff could not be prepared. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }
}

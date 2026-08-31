package com.memberconnect.backend.service.finance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.memberconnect.backend.event.MemberTransferApprovedEvent;
import com.memberconnect.backend.service.MemberTransferService;

/**
 * Hands a district change to the Finance Module once the approval has committed
 * (SRS MMC30).
 *
 * AFTER_COMMIT for the reason the other finance listeners give: approving a transfer
 * rewrites the member's profile, and calling a downstream module inline would hold that
 * transaction open across a network round trip, letting one unreachable system roll
 * back an approval that has already been decided.
 */
@Component
public class FinanceMemberTransferListener {

    private static final Logger log = LoggerFactory.getLogger(FinanceMemberTransferListener.class);

    private final MemberTransferService memberTransferService;
    private final FinanceMemberTransferClient financeClient;

    public FinanceMemberTransferListener(
            MemberTransferService memberTransferService,
            FinanceMemberTransferClient financeClient
    ) {
        this.memberTransferService = memberTransferService;
        this.financeClient = financeClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberTransferApproved(MemberTransferApprovedEvent event) {
        try {
            financeClient.sendMemberRelocated(
                    memberTransferService.buildRelocationHandoff(event)
            );
        } catch (Exception e) {
            // The client already swallows transport failures, so reaching this means the
            // payload could not even be assembled. Caught anyway: throwing from an
            // after-commit callback cannot undo the commit, it only fails an operation
            // that already succeeded.
            log.error(
                    "Finance relocation handoff could not be prepared. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }
}

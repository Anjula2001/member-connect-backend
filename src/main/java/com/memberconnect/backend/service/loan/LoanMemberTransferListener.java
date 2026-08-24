package com.memberconnect.backend.service.loan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.memberconnect.backend.event.MemberTransferApprovedEvent;
import com.memberconnect.backend.service.MemberTransferService;

/**
 * Hands a district change to the Loan Module once the approval has committed
 * (SRS MMC30).
 *
 * A separate listener from the Finance one on purpose: the two modules are separate
 * systems, and one being unreachable must not stop the other from being told. Sharing a
 * listener would put both handoffs behind the same try/catch and the same failure.
 */
@Component
public class LoanMemberTransferListener {

    private static final Logger log = LoggerFactory.getLogger(LoanMemberTransferListener.class);

    private final MemberTransferService memberTransferService;
    private final LoanMemberTransferClient loanClient;

    public LoanMemberTransferListener(
            MemberTransferService memberTransferService,
            LoanMemberTransferClient loanClient
    ) {
        this.memberTransferService = memberTransferService;
        this.loanClient = loanClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberTransferApproved(MemberTransferApprovedEvent event) {
        try {
            loanClient.sendMemberRelocated(
                    memberTransferService.buildRelocationHandoff(event)
            );
        } catch (Exception e) {
            log.error(
                    "Loan relocation handoff could not be prepared. memberId={}, requestNo={}, cause={}",
                    event.memberId(), event.requestNo(), e.toString(), e
            );
        }
    }
}

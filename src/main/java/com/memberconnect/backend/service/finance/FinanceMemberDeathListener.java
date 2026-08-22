package com.memberconnect.backend.service.finance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.memberconnect.backend.event.MemberDeathApprovedEvent;
import com.memberconnect.backend.service.MemberDeathRecordService;

/**
 * Hands an approved Member Death Record to the Finance Module (SRS MMT25).
 *
 * AFTER_COMMIT, so Finance never sees a record that a later rollback would have
 * erased. The payload is assembled by the service rather than by the client, so
 * the client stays a transport and the entity graph is read inside a
 * transaction - mirrors FinanceTerminationListener.
 */
@Component
public class FinanceMemberDeathListener {

    private static final Logger log = LoggerFactory.getLogger(FinanceMemberDeathListener.class);

    private final FinanceMemberDeathClient financeClient;
    private final MemberDeathRecordService memberDeathRecordService;

    public FinanceMemberDeathListener(
            FinanceMemberDeathClient financeClient,
            MemberDeathRecordService memberDeathRecordService
    ) {
        this.financeClient = financeClient;
        this.memberDeathRecordService = memberDeathRecordService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberDeathApproved(MemberDeathApprovedEvent event) {
        try {
            financeClient.sendMemberDeathApproved(
                    memberDeathRecordService.buildFinanceHandoff(event.recordNo()));
        } catch (Exception e) {
            log.error(
                    "Member death finance handoff failed unexpectedly. memberId={}, recordNo={}, cause={}",
                    event.memberId(), event.recordNo(), e.toString(), e
            );
        }
    }
}

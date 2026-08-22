package com.memberconnect.backend.service.finance;

import com.memberconnect.backend.dto.FinanceMemberDeathHandoffDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sends approved Member Death Records to the Finance Module for account closing
 * and fund disbursement (SRS MMT25).
 *
 * Switched off by default, exactly like FinanceTerminationClient: with
 * finance.integration.enabled=false the handoff is logged and nothing is sent,
 * so the whole death workflow runs as it will in production and the member
 * simply waits at MEMBER_DEATH_APPROVED until someone calls the completion
 * endpoint.
 *
 * The contract in both directions:
 *   out - POST {finance.base-url}/api/member-deaths  with FinanceMemberDeathHandoffDTO
 *   in  - PATCH /api/finance/member-deaths/{recordNo}/complete
 *
 * A failure here is logged rather than thrown: the approval is already committed
 * and must not be undone by an unreachable downstream system. A handoff that
 * fails to send is recovered by retrying it, not by rolling back a decision.
 */
@Component
public class FinanceMemberDeathClient {

    private static final Logger log = LoggerFactory.getLogger(FinanceMemberDeathClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final RestClient restClient;

    public FinanceMemberDeathClient(
            @Value("${finance.integration.enabled:false}") boolean enabled,
            @Value("${finance.base-url:}") String baseUrl,
            RestClient.Builder restClientBuilder
    ) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.restClient = restClientBuilder.build();
    }

    public void sendMemberDeathApproved(FinanceMemberDeathHandoffDTO handoff) {
        if (!enabled) {
            log.info(
                    "Finance integration disabled - member death handoff not sent. "
                            + "recordNo={}, memberId={}, deceasedDate={}, disburseAmount={}, minorAccounts={}",
                    handoff.getRecordNo(),
                    handoff.getMemberId(),
                    handoff.getDeceasedDate(),
                    handoff.getDisburseDonationAmount(),
                    handoff.getMinorDisbursements().size()
            );
            return;
        }

        if (baseUrl.isEmpty()) {
            // Enabled without a target is a deployment mistake, not a normal
            // state, so it is logged at error rather than passed over quietly.
            log.error(
                    "Finance integration is enabled but finance.base-url is not set. "
                            + "Member death handoff not sent. recordNo={}",
                    handoff.getRecordNo()
            );
            return;
        }

        try {
            restClient.post()
                    .uri(baseUrl + "/api/member-deaths")
                    .body(handoff)
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "Member death handoff sent to Finance Module. recordNo={}, memberId={}",
                    handoff.getRecordNo(), handoff.getMemberId()
            );
        } catch (Exception e) {
            log.error(
                    "Member death handoff to Finance Module failed. recordNo={}, memberId={}, cause={}",
                    handoff.getRecordNo(), handoff.getMemberId(), e.toString(), e
            );
        }
    }
}

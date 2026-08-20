package com.memberconnect.backend.service.finance;

import com.memberconnect.backend.dto.FinanceTerminationHandoffDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sends board-approved terminations to the Finance Module for account closing
 * (SRS MMT11).
 *
 * The Finance Module does not exist yet, so this is switched off by default.
 * With finance.integration.enabled=false the handoff is logged and nothing is
 * sent - the rest of the termination workflow runs exactly as it will in
 * production, and the member simply waits at TERMINATION_APPROVED until someone
 * calls the completion endpoint. That mirrors how LoggingSmsSender stands in for
 * an SMS gateway that has not been procured.
 *
 * The contract in both directions:
 *   out - POST {finance.base-url}/api/terminations  with FinanceTerminationHandoffDTO
 *   in  - PATCH /api/finance/terminations/{requestNo}/complete  (FinanceCallbackController)
 *
 * Finance replies asynchronously, which is why a failure here is logged rather
 * than thrown: the board's decision is already committed and must not be undone
 * by an unreachable downstream system. A handoff that fails to send is recovered
 * by retrying it, not by rolling back a meeting.
 */
@Component
public class FinanceTerminationClient {

    private static final Logger log = LoggerFactory.getLogger(FinanceTerminationClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final RestClient restClient;

    public FinanceTerminationClient(
            @Value("${finance.integration.enabled:false}") boolean enabled,
            @Value("${finance.base-url:}") String baseUrl,
            RestClient.Builder restClientBuilder
    ) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.restClient = restClientBuilder.build();
    }

    public void sendTerminationApproved(FinanceTerminationHandoffDTO handoff) {
        if (!enabled) {
            log.info(
                    "Finance integration disabled - termination handoff not sent. "
                            + "requestNo={}, memberId={}, effectiveDate={}, minorAccounts={}",
                    handoff.getRequestNo(),
                    handoff.getMemberId(),
                    handoff.getEffectiveDate(),
                    handoff.getMinorDisbursements().size()
            );
            return;
        }

        if (baseUrl.isEmpty()) {
            // Enabled without a target is a deployment mistake, not a normal
            // state, so it is logged at error rather than passed over quietly.
            log.error(
                    "Finance integration is enabled but finance.base-url is not set. "
                            + "Termination handoff not sent. requestNo={}",
                    handoff.getRequestNo()
            );
            return;
        }

        try {
            restClient.post()
                    .uri(baseUrl + "/api/terminations")
                    .body(handoff)
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "Termination handoff sent to Finance Module. requestNo={}, memberId={}",
                    handoff.getRequestNo(), handoff.getMemberId()
            );
        } catch (Exception e) {
            log.error(
                    "Termination handoff to Finance Module failed. requestNo={}, memberId={}, cause={}",
                    handoff.getRequestNo(), handoff.getMemberId(), e.toString(), e
            );
        }
    }
}

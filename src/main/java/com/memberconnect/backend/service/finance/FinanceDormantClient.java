package com.memberconnect.backend.service.finance;

import com.memberconnect.backend.dto.FinanceDormantHandoffDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Tells the Finance Module to flag the accounts of a member the Board has made
 * Inactive (Dormant) (SRS 4.2.7).
 *
 * The Finance Module does not exist yet, so this is switched off by default.
 * With finance.integration.enabled=false the handoff is logged and nothing is
 * sent, and the rest of the dormant workflow runs exactly as it will in
 * production.
 *
 * <h2>Fire-and-forget, unlike terminations - and the SRS verbs are the reason</h2>
 *
 * FinanceTerminationClient has a callback: MMT11 says Finance <em>closes</em> the
 * accounts and only then does the member become TERMINATED, so the status is a
 * consequence of Finance's work and MemberConnect has to wait for it. SRS 4.2.7
 * says Finance merely <em>flags</em> the relevant accounts as Dormant - Finance's
 * work is a consequence of the Board's decision, not a precondition for it.
 * Copying the termination handshake here would invert the causality.
 *
 * Two supporting reasons. INACTIVE_DORMANT is undone by a single remittance,
 * where TERMINATED is terminal and moves money - the two-phase handshake exists
 * to stop MemberConnect declaring a membership over before the funds actually
 * left, and nothing equivalent is at stake here. And a dormant list is a bulk
 * operation: a meeting may carry two hundred members, so an await-confirmation
 * design would leave a list indefinitely half-confirmed whenever Finance is
 * unreachable, doubling the state space of a screen whose entire value is
 * showing one clear outcome per meeting.
 *
 * The honest cost of that choice: if Finance ever needs to REFUSE a flagging,
 * recovery is a manual reactivation. That is smaller and more visible than a
 * permanently pending state. A failed handoff is recovered by retrying the
 * handoff - the error line below names the listId - not by rolling back a
 * meeting the Board has already held.
 *
 * The contract, one direction only:
 *   out - POST {finance.base-url}/api/dormant-members  with FinanceDormantHandoffDTO
 */
@Component
public class FinanceDormantClient {

    private static final Logger log = LoggerFactory.getLogger(FinanceDormantClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final RestClient restClient;

    public FinanceDormantClient(
            @Value("${finance.integration.enabled:false}") boolean enabled,
            @Value("${finance.base-url:}") String baseUrl,
            RestClient.Builder restClientBuilder
    ) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.restClient = restClientBuilder.build();
    }

    public void sendDormantInactivated(FinanceDormantHandoffDTO handoff) {
        if (!enabled) {
            log.info(
                    "Finance integration disabled - dormant handoff not sent. "
                            + "memberId={}, listId={}, boardMeetingDate={}",
                    handoff.getMemberId(), handoff.getListId(), handoff.getBoardMeetingDate()
            );
            return;
        }

        if (baseUrl.isEmpty()) {
            // Enabled without a target is a deployment mistake, not a normal
            // state, so it is logged at error rather than passed over quietly.
            log.error(
                    "Finance integration is enabled but finance.base-url is not set. "
                            + "Dormant handoff not sent. memberId={}, listId={}",
                    handoff.getMemberId(), handoff.getListId()
            );
            return;
        }

        try {
            restClient.post()
                    .uri(baseUrl + "/api/dormant-members")
                    .body(handoff)
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "Dormant handoff sent to Finance Module. memberId={}, listId={}",
                    handoff.getMemberId(), handoff.getListId()
            );
        } catch (Exception e) {
            // Logged, never thrown. The Board's decision is already committed and
            // must not be undone by an unreachable downstream system.
            log.error(
                    "Dormant handoff to Finance Module failed. memberId={}, listId={}, cause={}",
                    handoff.getMemberId(), handoff.getListId(), e.toString(), e
            );
        }
    }
}

package com.memberconnect.backend.service.finance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.memberconnect.backend.dto.MemberRelocationHandoffDTO;

/**
 * Asks the Finance Module to move a member's savings accounts to their new District
 * Office after an approved transfer (SRS MMC30).
 *
 * The Finance Module does not exist yet, so this is switched off by default. With
 * finance.integration.enabled=false the handoff is logged and nothing is sent - the
 * transfer workflow runs exactly as it will in production, and the accounts simply stay
 * where they are until the integration is built. That mirrors FinanceTerminationClient
 * and LoggingSmsSender.
 *
 * Contract: POST {finance.base-url}/api/member-transfers with MemberRelocationHandoffDTO.
 * One direction only - the SRS describes a message being sent, not a conversation - so
 * there is no callback for Finance to reply on.
 *
 * A failure is logged rather than thrown: the approval is already committed and the
 * member's profile already updated, and neither can be undone by an unreachable
 * downstream system. A handoff that fails to send is recovered by resending it.
 */
@Component
public class FinanceMemberTransferClient {

    private static final Logger log = LoggerFactory.getLogger(FinanceMemberTransferClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final RestClient restClient;

    public FinanceMemberTransferClient(
            @Value("${finance.integration.enabled:false}") boolean enabled,
            @Value("${finance.base-url:}") String baseUrl,
            RestClient.Builder restClientBuilder
    ) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.restClient = restClientBuilder.build();
    }

    public void sendMemberRelocated(MemberRelocationHandoffDTO handoff) {
        if (!enabled) {
            log.info(
                    "Finance integration disabled - member relocation handoff not sent. "
                            + "requestNo={}, memberId={}, fromDistrict={}, toDistrict={}",
                    handoff.getRequestNo(), handoff.getMemberId(),
                    handoff.getFromDistrict(), handoff.getToDistrict()
            );
            return;
        }

        if (baseUrl.isEmpty()) {
            // Enabled without a target is a deployment mistake, not a normal state,
            // so it is logged at error rather than passed over quietly.
            log.error(
                    "Finance integration is enabled but finance.base-url is not set. "
                            + "Member relocation handoff not sent. requestNo={}",
                    handoff.getRequestNo()
            );
            return;
        }

        try {
            restClient.post()
                    .uri(baseUrl + "/api/member-transfers")
                    .body(handoff)
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "Member relocation handoff sent to Finance Module. requestNo={}, memberId={}, toDistrict={}",
                    handoff.getRequestNo(), handoff.getMemberId(), handoff.getToDistrict()
            );
        } catch (Exception e) {
            log.error(
                    "Member relocation handoff to Finance Module failed. requestNo={}, memberId={}, cause={}",
                    handoff.getRequestNo(), handoff.getMemberId(), e.toString(), e
            );
        }
    }
}

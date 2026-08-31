package com.memberconnect.backend.service.loan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.memberconnect.backend.dto.MemberRelocationHandoffDTO;

/**
 * Asks the Loan Module to move a member's loans to their new District Office after an
 * approved transfer (SRS MMC30).
 *
 * The Loan Module is not integrated - the Loan table in this system is a read-only stub
 * of member id and balance, with no district of its own - so this is switched off by
 * default. With loan.integration.enabled=false the handoff is logged and nothing is
 * sent, which is the same stand-in arrangement the Finance clients use.
 *
 * Contract: POST {loan.base-url}/api/member-transfers with MemberRelocationHandoffDTO.
 * Deliberately the same payload the Finance Module receives: both are being asked to
 * re-file the same member under the same new office.
 *
 * A failure is logged rather than thrown, for the same reason as the Finance client:
 * the approval has already committed and must not be undone by an unreachable module.
 */
@Component
public class LoanMemberTransferClient {

    private static final Logger log = LoggerFactory.getLogger(LoanMemberTransferClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final RestClient restClient;

    public LoanMemberTransferClient(
            @Value("${loan.integration.enabled:false}") boolean enabled,
            @Value("${loan.base-url:}") String baseUrl,
            RestClient.Builder restClientBuilder
    ) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.restClient = restClientBuilder.build();
    }

    public void sendMemberRelocated(MemberRelocationHandoffDTO handoff) {
        if (!enabled) {
            log.info(
                    "Loan integration disabled - member relocation handoff not sent. "
                            + "requestNo={}, memberId={}, fromDistrict={}, toDistrict={}",
                    handoff.getRequestNo(), handoff.getMemberId(),
                    handoff.getFromDistrict(), handoff.getToDistrict()
            );
            return;
        }

        if (baseUrl.isEmpty()) {
            log.error(
                    "Loan integration is enabled but loan.base-url is not set. "
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
                    "Member relocation handoff sent to Loan Module. requestNo={}, memberId={}, toDistrict={}",
                    handoff.getRequestNo(), handoff.getMemberId(), handoff.getToDistrict()
            );
        } catch (Exception e) {
            log.error(
                    "Member relocation handoff to Loan Module failed. requestNo={}, memberId={}, cause={}",
                    handoff.getRequestNo(), handoff.getMemberId(), e.toString(), e
            );
        }
    }
}

package com.memberconnect.backend.service.finance;

import com.memberconnect.backend.dto.DeathDonationFinanceSnapshotDTO;
import com.memberconnect.backend.dto.FinanceDeathDonationHandoffDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads the Finance-owned figures behind a Death Donation entitlement (SRS 4.2.3):
 * how many months the death donation remittance was deducted, what the member has
 * already received in the past 12 months, and the Special Fixed Account for
 * Funerals balance.
 *
 * Off by default. None of these figures exist in this database - there is no
 * member-level death donation remittance ledger and no funeral account table -
 * so with the integration disabled this returns a zeroed snapshot rather than
 * inventing numbers. That is a usable state, not a broken one: the SRS makes all
 * three inputs user-editable precisely because the operator may need to correct
 * what Finance reports.
 *
 * Never throws. A Death Donation panel that cannot reach Finance must still open
 * so the record can be worked on by hand.
 */
@Component
public class FinanceDeathDonationClient {

    private static final Logger log = LoggerFactory.getLogger(FinanceDeathDonationClient.class);

    private final boolean enabled;
    private final boolean handoffEnabled;
    private final String baseUrl;
    private final RestClient restClient;

    public FinanceDeathDonationClient(
            @Value("${finance.death-donation.enabled:false}") boolean enabled,
            @Value("${finance.integration.enabled:false}") boolean handoffEnabled,
            @Value("${finance.base-url:}") String baseUrl,
            RestClient.Builder restClientBuilder
    ) {
        this.enabled = enabled;
        this.handoffEnabled = handoffEnabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.restClient = restClientBuilder.build();
    }

    /**
     * MMD08: hands an approved Death Donation Request to the Finance Module for
     * disbursement. Contract: POST {finance.base-url}/api/death-donations.
     *
     * Gated on finance.integration.enabled - the same switch the termination and
     * member death handoffs use, because all three are the same kind of outbound
     * write and should go live together.
     *
     * Never throws. The approval is already committed and must not be undone by
     * an unreachable downstream system; a handoff that fails to send is recovered
     * by retrying it, not by rolling back a decision.
     */
    public void sendDeathDonationApproved(FinanceDeathDonationHandoffDTO handoff) {
        if (!handoffEnabled) {
            log.info(
                    "Finance integration disabled - death donation handoff not sent. "
                            + "requestNo={}, memberId={}, creditedToFund={}, disburseAmount={}",
                    handoff.getRequestNo(),
                    handoff.getMemberId(),
                    handoff.getCreditedToSpecialFixedAccount(),
                    handoff.getDisburseDonationAmount()
            );
            return;
        }

        if (baseUrl.isEmpty()) {
            // Enabled without a target is a deployment mistake, not a normal
            // state, so it is logged at error rather than passed over quietly.
            log.error(
                    "Finance integration is enabled but finance.base-url is not set. "
                            + "Death donation handoff not sent. requestNo={}",
                    handoff.getRequestNo()
            );
            return;
        }

        try {
            restClient.post()
                    .uri(baseUrl + "/api/death-donations")
                    .body(handoff)
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "Death donation handoff sent to Finance Module. requestNo={}, memberId={}",
                    handoff.getRequestNo(), handoff.getMemberId()
            );
        } catch (Exception e) {
            log.error(
                    "Death donation handoff to Finance Module failed. requestNo={}, memberId={}, cause={}",
                    handoff.getRequestNo(), handoff.getMemberId(), e.toString(), e
            );
        }
    }

    public DeathDonationFinanceSnapshotDTO fetchSnapshot(String memberId) {
        if (!enabled) {
            log.info(
                    "Finance death donation lookup disabled - returning an empty snapshot. memberId={}",
                    memberId
            );
            return DeathDonationFinanceSnapshotDTO.empty();
        }

        if (baseUrl.isEmpty()) {
            // Enabled without a target is a deployment mistake, not a normal
            // state, so it is logged at error rather than passed over quietly.
            log.error(
                    "Finance death donation lookup is enabled but finance.base-url is not set. memberId={}",
                    memberId
            );
            return DeathDonationFinanceSnapshotDTO.empty();
        }

        try {
            DeathDonationFinanceSnapshotDTO snapshot = restClient.get()
                    .uri(baseUrl + "/api/members/" + memberId + "/death-donation-snapshot")
                    .retrieve()
                    .body(DeathDonationFinanceSnapshotDTO.class);

            if (snapshot == null) {
                log.warn("Finance returned no death donation snapshot. memberId={}", memberId);
                return DeathDonationFinanceSnapshotDTO.empty();
            }

            return snapshot;
        } catch (Exception e) {
            log.error(
                    "Finance death donation lookup failed, falling back to an empty snapshot. "
                            + "memberId={}, cause={}",
                    memberId, e.toString(), e
            );
            return DeathDonationFinanceSnapshotDTO.empty();
        }
    }
}

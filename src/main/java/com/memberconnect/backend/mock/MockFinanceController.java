package com.memberconnect.backend.mock;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.DeathDonationFinanceSnapshotDTO;
import com.memberconnect.backend.dto.FinanceDeathDonationHandoffDTO;
import com.memberconnect.backend.dto.FinanceDormantHandoffDTO;

/**
 * A stand-in for the Finance Module, so the Death Donation Details panel shows
 * realistic figures before Finance exists.
 *
 * This deliberately speaks the real contract over real HTTP rather than stubbing
 * FinanceDeathDonationClient: pointing {@code finance.base-url} here exercises
 * the production client, its serialisation and its error handling, so the only
 * thing swapped out on the day Finance goes live is one URL. A mock injected
 * behind the client would test none of that.
 *
 * <strong>Never registered unless {@code finance.mock.enabled=true}.</strong> With
 * the flag off the bean does not exist and every path below answers 404,
 * regardless of the security allow-list entry that accompanies it.
 *
 * Delete this package once the Finance Module is available.
 */
@RestController
@RequestMapping("/mock-finance")
@ConditionalOnProperty(name = "finance.mock.enabled", havingValue = "true")
public class MockFinanceController {

    private static final Logger log = LoggerFactory.getLogger(MockFinanceController.class);

    /**
     * Eight member profiles, chosen so that a handful of members between them
     * exercise every branch of the entitlement calculation: below the first tier,
     * each tier band, the past-12-month deduction, the funeral-account multiplier,
     * a partly filled funeral account and a completely full one.
     *
     * Read against the seeded configuration (maximum 100,000; funeral cap 200,000;
     * multiplier 2; tiers 0/12/24/60 months at 0/25/50/100%).
     */
    private static final MockProfile[] PROFILES = {
            // months, received in past 12 months, has funeral account, credited to it
            new MockProfile(8, "0", false, "0"),          // not yet eligible: 0%
            new MockProfile(18, "0", false, "0"),         // 25% -> 25,000 payable
            new MockProfile(31, "0", false, "0"),         // 50% -> 50,000 payable
            new MockProfile(76, "0", false, "0"),         // 100% -> 100,000 payable
            new MockProfile(64, "15000", false, "0"),     // 100% less 15,000 already paid
            new MockProfile(40, "0", true, "120000"),     // x2, 80,000 headroom absorbs part
            new MockProfile(96, "0", true, "185000"),     // x2, only 15,000 headroom left
            new MockProfile(27, "5000", true, "200000"),  // x2, funeral account already full
    };

    private record MockProfile(
            int monthsRemitted,
            String receivedPast12Months,
            boolean hasFuneralAccount,
            String funeralAccountCredited
    ) {}

    /**
     * The read the Death Donation entitlement is built from.
     *
     * Keyed off the member id so a member always shows the same figures. A demo
     * where the numbers changed on every reload would be worse than no numbers at
     * all - nobody could tell a recalculation from a reshuffle.
     */
    @GetMapping("/api/members/{memberId}/death-donation-snapshot")
    public DeathDonationFinanceSnapshotDTO getDeathDonationSnapshot(@PathVariable String memberId) {
        MockProfile profile = profileFor(memberId);

        DeathDonationFinanceSnapshotDTO snapshot = new DeathDonationFinanceSnapshotDTO();
        snapshot.setMonthsRemitted(profile.monthsRemitted());
        snapshot.setReceivedPast12Months(new BigDecimal(profile.receivedPast12Months()));

        if (profile.hasFuneralAccount()) {
            snapshot.setFuneralAccountNo("SFA-" + memberId);
            snapshot.setFuneralAccountCredited(new BigDecimal(profile.funeralAccountCredited()));
        } else {
            snapshot.setFuneralAccountNo(null);
            snapshot.setFuneralAccountCredited(BigDecimal.ZERO);
        }

        log.info(
                "MOCK Finance: death donation snapshot served. memberId={}, monthsRemitted={}, "
                        + "receivedPast12Months={}, funeralAccountNo={}",
                memberId, snapshot.getMonthsRemitted(), snapshot.getReceivedPast12Months(),
                snapshot.getFuneralAccountNo()
        );

        return snapshot;
    }

    /**
     * The MMD08 hand-off sink, so an approval completes end to end and the payload
     * Finance would have received is visible in the log.
     */
    @PostMapping("/api/death-donations")
    public ResponseEntity<Map<String, String>> receiveDeathDonation(
            @RequestBody FinanceDeathDonationHandoffDTO handoff
    ) {
        log.info(
                "MOCK Finance: death donation handoff received. requestNo={}, memberId={}, "
                        + "approvalLevel={}, creditedToFund={}, disburseAmount={}",
                handoff.getRequestNo(), handoff.getMemberId(), handoff.getApprovalLevel(),
                handoff.getCreditedToSpecialFixedAccount(), handoff.getDisburseDonationAmount()
        );

        return ResponseEntity.ok(Map.of(
                "status", "ACCEPTED",
                "requestNo", String.valueOf(handoff.getRequestNo())
        ));
    }

    /**
     * The SRS 4.2.7 hand-off sink, so a board decision completes end to end and
     * the payload Finance would have received is visible in the log.
     *
     * Note there is no callback counterpart here, unlike terminations: dormancy
     * is fire-and-forget by design. See FinanceDormantClient for why.
     */
    @PostMapping("/api/dormant-members")
    public ResponseEntity<Map<String, String>> receiveDormantInactivation(
            @RequestBody FinanceDormantHandoffDTO handoff
    ) {
        log.info(
                "MOCK Finance: dormant handoff received - accounts flagged dormant. "
                        + "memberId={}, listId={}, boardMeetingDate={}, inactivatedOn={}",
                handoff.getMemberId(), handoff.getListId(),
                handoff.getBoardMeetingDate(), handoff.getInactivatedOn()
        );

        return ResponseEntity.ok(Map.of(
                "status", "ACCEPTED",
                "memberId", String.valueOf(handoff.getMemberId())
        ));
    }

    /**
     * Stable bucket for a member id.
     *
     * floorMod rather than Math.abs: abs(Integer.MIN_VALUE) is still negative, and
     * a single member id hashing to that value would throw here instead of showing
     * a figure.
     */
    private MockProfile profileFor(String memberId) {
        String key = memberId == null ? "" : memberId.trim();
        return PROFILES[Math.floorMod(key.hashCode(), PROFILES.length)];
    }
}

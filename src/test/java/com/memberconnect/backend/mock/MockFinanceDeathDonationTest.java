package com.memberconnect.backend.mock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.memberconnect.backend.dto.DeathDonationFinanceSnapshotDTO;

/**
 * The stand-in Finance Module's data.
 *
 * Worth pinning even though it is a mock: these figures are what a demo or UAT
 * session is judged on, and a snapshot that changed between two page loads, or
 * that only ever landed in one tier, would make the entitlement calculation look
 * broken when it was not.
 *
 * No Spring context and no database.
 */
class MockFinanceDeathDonationTest {

    private final MockFinanceController controller = new MockFinanceController();

    /** The same member must always show the same figures. */
    @Test
    void aSnapshotIsStableForAGivenMember() {
        DeathDonationFinanceSnapshotDTO first = controller.getDeathDonationSnapshot("M1001");
        DeathDonationFinanceSnapshotDTO second = controller.getDeathDonationSnapshot("M1001");

        assertThat(second.getMonthsRemitted()).isEqualTo(first.getMonthsRemitted());
        assertThat(second.getReceivedPast12Months())
                .isEqualByComparingTo(first.getReceivedPast12Months());
        assertThat(second.getFuneralAccountNo()).isEqualTo(first.getFuneralAccountNo());
    }

    /**
     * Across a realistic set of member ids the mock must reach every tier band,
     * or the demo silently only ever shows one of them.
     */
    @Test
    void theProfilesSpanEveryEligibilityTier() {
        Set<String> bands = new HashSet<>();

        IntStream.rangeClosed(1, 200)
                .mapToObj(index -> "M" + (1000 + index))
                .map(controller::getDeathDonationSnapshot)
                .forEach(snapshot -> bands.add(bandOf(snapshot.getMonthsRemitted())));

        assertThat(bands)
                .as("a demo should be able to show every tier, not just one")
                .contains("0%", "25%", "50%", "100%");
    }

    /** Both the funeral-account branch and the plain branch must be reachable. */
    @Test
    void someMembersHoldAFuneralAccountAndSomeDoNot() {
        long withAccount = IntStream.rangeClosed(1, 200)
                .mapToObj(index -> "M" + (1000 + index))
                .map(controller::getDeathDonationSnapshot)
                .filter(snapshot -> snapshot.getFuneralAccountNo() != null)
                .count();

        assertThat(withAccount).isGreaterThan(0);
        assertThat(withAccount).isLessThan(200);
    }

    /** A member with no funeral account must not carry a credited balance. */
    @Test
    void aMemberWithoutAFuneralAccountHasNothingCreditedToOne() {
        IntStream.rangeClosed(1, 200)
                .mapToObj(index -> "M" + (1000 + index))
                .map(controller::getDeathDonationSnapshot)
                .filter(snapshot -> snapshot.getFuneralAccountNo() == null)
                .forEach(snapshot -> assertThat(snapshot.getFuneralAccountCredited())
                        .isEqualByComparingTo(BigDecimal.ZERO));
    }

    /**
     * hashCode can be negative, and Math.abs(Integer.MIN_VALUE) is still negative -
     * an id that hashed to it would have thrown rather than shown a figure.
     */
    @Test
    void anyMemberIdResolvesToAProfile() {
        assertThat(controller.getDeathDonationSnapshot("")).isNotNull();
        assertThat(controller.getDeathDonationSnapshot(null)).isNotNull();
        assertThat(controller.getDeathDonationSnapshot("polygenelubricants")).isNotNull();
    }

    private String bandOf(Integer monthsRemitted) {
        int months = monthsRemitted == null ? 0 : monthsRemitted;
        if (months >= 60) return "100%";
        if (months >= 24) return "50%";
        if (months >= 12) return "25%";
        return "0%";
    }
}

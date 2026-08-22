package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.memberconnect.backend.config.DeathDonationConfigSeeder;
import com.memberconnect.backend.model.DeathDonationConfig;
import com.memberconnect.backend.model.DeathDonationEligibilityTier;
import com.memberconnect.backend.model.DeathDonationRequest;
import com.memberconnect.backend.repository.CauseOfDeathRepository;
import com.memberconnect.backend.repository.DeathDonationConfigRepository;
import com.memberconnect.backend.repository.DeathDonationEligibilityTierRepository;
import com.memberconnect.backend.service.finance.FinanceDeathDonationClient;

/**
 * The Death Donation entitlement arithmetic (SRS 2.2.3), exercised through a
 * DeathDonationRequest.
 *
 * The same service also serves MemberDeathRecord; these cases pin the shape of
 * the calculation itself - tier banding, the funeral-account multiplier, the
 * headroom cap, and the two safety floors - rather than either caller.
 *
 * Pure Mockito: no Spring context and no database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeathDonationEntitlementTest {

    @Mock private DeathDonationConfigRepository configRepository;
    @Mock private DeathDonationEligibilityTierRepository tierRepository;
    @Mock private CauseOfDeathRepository causeOfDeathRepository;
    @Mock private FinanceDeathDonationClient financeClient;

    @InjectMocks private DeathDonationEntitlementService service;

    private void givenStandardConfig() {
        givenConfig(DeathDonationConfigSeeder.DEFAULT_MAX_DONATION, "100000.00");
        givenConfig(DeathDonationConfigSeeder.FUNERAL_ACCOUNT_MULTIPLIER, "2.00");
        givenConfig(DeathDonationConfigSeeder.FUNERAL_ACCOUNT_MAXIMUM, "200000.00");
        givenConfig(DeathDonationConfigSeeder.DONATION_ELIGIBLE_PERIOD_DAYS, "90");

        when(tierRepository.findAllByOrderByMinMonthsDesc()).thenReturn(List.of(
                tier(60, "100.00"),
                tier(24, "50.00"),
                tier(12, "25.00"),
                tier(0, "0.00")
        ));
    }

    @Test
    void theTierBandSetsTheEligibleShareOfTheMaximum() {
        givenStandardConfig();

        DeathDonationRequest request = newRequest();
        request.setMonthsRemitted(24);

        service.recalculate(request);

        assertThat(request.getMaximumDonationAmount()).isEqualByComparingTo("100000.00");
        assertThat(request.getEligibleDonationAmount()).isEqualByComparingTo("50000.00");
        assertThat(request.getDisburseDonationAmount()).isEqualByComparingTo("50000.00");
    }

    /** A member sits in the highest band they have actually reached, not the next one. */
    @Test
    void aMemberJustShortOfABandStaysInTheOneBelow() {
        givenStandardConfig();

        DeathDonationRequest request = newRequest();
        request.setMonthsRemitted(23);

        service.recalculate(request);

        assertThat(request.getEligibleDonationAmount()).isEqualByComparingTo("25000.00");
    }

    @Test
    void donationsAlreadyReceivedThisYearComeOffTheEntitlement() {
        givenStandardConfig();

        DeathDonationRequest request = newRequest();
        request.setMonthsRemitted(60);
        request.setReceivedPast12Months(new BigDecimal("30000.00"));

        service.recalculate(request);

        assertThat(request.getEligibleDonationAmount()).isEqualByComparingTo("100000.00");
        assertThat(request.getDisburseDonationAmount()).isEqualByComparingTo("70000.00");
    }

    /** Never negative: a member who has already had more than they are due gets zero. */
    @Test
    void anOverpaidMemberIsFlooredAtZeroRatherThanGoingNegative() {
        givenStandardConfig();

        DeathDonationRequest request = newRequest();
        request.setMonthsRemitted(12);
        request.setReceivedPast12Months(new BigDecimal("999999.00"));

        service.recalculate(request);

        assertThat(request.getDisburseDonationAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void aFuneralAccountDoublesBothFiguresAndAbsorbsUpToItsHeadroom() {
        givenStandardConfig();

        DeathDonationRequest request = newRequest();
        request.setMonthsRemitted(60);
        request.setFuneralAccountNo("FA-1");
        request.setFuneralAccountCredited(new BigDecimal("150000.00"));
        request.setFuneralAccountMaximum(new BigDecimal("200000.00"));

        service.recalculate(request);

        assertThat(request.getDonationMultiplierApplied()).isEqualByComparingTo("2.00");
        assertThat(request.getMaximumDonationAmount()).isEqualByComparingTo("200000.00");
        assertThat(request.getEligibleDonationAmount()).isEqualByComparingTo("200000.00");

        // 200,000 headroom is 50,000; the rest is paid out.
        assertThat(request.getCreditedToSpecialFixedAccount()).isEqualByComparingTo("50000.00");
        assertThat(request.getDisburseDonationAmount()).isEqualByComparingTo("150000.00");
    }

    @Test
    void withoutAFuneralAccountNothingIsCreditedAndNoMultiplierApplies() {
        givenStandardConfig();

        DeathDonationRequest request = newRequest();
        request.setMonthsRemitted(60);

        service.recalculate(request);

        assertThat(request.getDonationMultiplierApplied()).isEqualByComparingTo("1.00");
        assertThat(request.getCreditedToSpecialFixedAccount()).isEqualByComparingTo("0.00");
    }

    /** An operator override cannot route out more than the entitlement holds. */
    @Test
    void anOverrideCreditIsClampedToTheEntitlement() {
        givenStandardConfig();

        DeathDonationRequest request = newRequest();
        request.setMonthsRemitted(12);
        request.setFuneralAccountNo("FA-1");
        request.setFuneralAccountCredited(BigDecimal.ZERO);
        request.setFuneralAccountMaximum(new BigDecimal("200000.00"));

        service.applyOverrides(request, null, null, new BigDecimal("999999.00"));

        assertThat(request.getCreditedToSpecialFixedEdited()).isTrue();
        assertThat(request.getCreditedToSpecialFixedAccount())
                .isEqualByComparingTo(request.getEligibleDonationAmount());
        assertThat(request.getDisburseDonationAmount()).isEqualByComparingTo("0.00");
    }

    /**
     * An unseeded tier master must not quietly authorise the maximum payout, so
     * the rate falls to zero rather than to "no restriction".
     */
    @Test
    void anUnseededTierMasterAuthorisesNothing() {
        givenConfig(DeathDonationConfigSeeder.DEFAULT_MAX_DONATION, "100000.00");
        when(tierRepository.findAllByOrderByMinMonthsDesc()).thenReturn(List.of());

        DeathDonationRequest request = newRequest();
        request.setMonthsRemitted(120);

        service.recalculate(request);

        assertThat(request.getEligibleDonationAmount()).isEqualByComparingTo("0.00");
        assertThat(request.getDisburseDonationAmount()).isEqualByComparingTo("0.00");
    }

    /**
     * A donation request carries no cause of death, so the per-cause override that
     * Requirement 04 allows must never be consulted for one.
     */
    @Test
    void aDonationRequestNeverPicksUpACauseOfDeathOverride() {
        givenStandardConfig();

        DeathDonationRequest request = newRequest();
        assertThat(request.getCauseOfDeath()).isNull();

        request.setMonthsRemitted(60);
        service.recalculate(request);

        assertThat(request.getMaximumDonationAmount()).isEqualByComparingTo("100000.00");
    }

    // ---- The eligible-period warning (SRS 2.2.1 / 2.2.3) ----

    @Test
    void aRequestRaisedInsideTheWindowRaisesNoWarning() {
        givenStandardConfig();

        LocalDate deceased = LocalDate.of(2026, 1, 1);
        assertThat(service.buildEligiblePeriodWarning(deceased, deceased.plusDays(90))).isNull();
    }

    @Test
    void aRequestRaisedOutsideTheWindowNamesTheLimitItExceeded() {
        givenStandardConfig();

        LocalDate deceased = LocalDate.of(2026, 1, 1);
        String warning = service.buildEligiblePeriodWarning(deceased, deceased.plusDays(120));

        assertThat(warning)
                .contains("120 days")
                .contains("90 day eligible period");
    }

    @Test
    void aMissingDateCannotProduceAWarning() {
        givenStandardConfig();

        assertThat(service.buildEligiblePeriodWarning(null, LocalDate.now())).isNull();
        assertThat(service.buildEligiblePeriodWarning(LocalDate.now(), null)).isNull();
    }

    // ---- Helpers ----

    private DeathDonationRequest newRequest() {
        DeathDonationRequest request = new DeathDonationRequest();
        request.setRequestNo("DD-2026-001");
        request.setRequestedDate(LocalDate.now());
        request.setDeceasedDate(LocalDate.now().minusDays(5));
        return request;
    }

    private void givenConfig(String key, String value) {
        DeathDonationConfig config = new DeathDonationConfig();
        config.setConfigKey(key);
        config.setConfigValue(new BigDecimal(value));
        when(configRepository.findByConfigKey(key)).thenReturn(Optional.of(config));
    }

    private DeathDonationEligibilityTier tier(int minMonths, String percentage) {
        DeathDonationEligibilityTier tier = new DeathDonationEligibilityTier();
        tier.setMinMonths(minMonths);
        tier.setPercentage(new BigDecimal(percentage));
        return tier;
    }
}

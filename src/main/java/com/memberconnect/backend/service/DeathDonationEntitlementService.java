package com.memberconnect.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memberconnect.backend.config.DeathDonationConfigSeeder;
import com.memberconnect.backend.dto.DeathDonationFinanceSnapshotDTO;
import com.memberconnect.backend.model.CauseOfDeath;
import com.memberconnect.backend.model.DeathDonationConfig;
import com.memberconnect.backend.model.DeathDonationEligibilityTier;
import com.memberconnect.backend.model.DonationEntitlementTarget;
import com.memberconnect.backend.model.MemberDeathRecord;
import com.memberconnect.backend.repository.CauseOfDeathRepository;
import com.memberconnect.backend.repository.DeathDonationConfigRepository;
import com.memberconnect.backend.repository.DeathDonationEligibilityTierRepository;
import com.memberconnect.backend.service.finance.FinanceDeathDonationClient;

/**
 * Works out what a deceased member is entitled to under the Death Donation rules
 * (SRS 4.2.3, "Death Donation Details").
 *
 * The calculation, in the order the SRS states it:
 *
 *   maximum  = cause-of-death override, else the configured general maximum
 *   eligible = maximum x the tier rate for the months remitted
 *
 *   with a Special Fixed Account for Funerals, BOTH figures are multiplied by
 *   the configured multiplier (default 2)
 *
 *   entitled = eligible - donations received in the past 12 months, floored at 0
 *
 *   the funeral account absorbs the entitlement up to its remaining headroom,
 *   and whatever is left over is what gets disbursed
 *
 * Three inputs stay the operator's to override - months remitted, donations
 * received in the past 12 months, and the amount credited to the funeral account.
 * Each carries an "edited" marker so the screen can show which figures were
 * changed by hand rather than derived.
 */
@Service
public class DeathDonationEntitlementService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MONEY_SCALE = 2;

    private static final BigDecimal FALLBACK_MAX_DONATION = BigDecimal.ZERO;
    private static final BigDecimal FALLBACK_MULTIPLIER = BigDecimal.ONE;
    private static final BigDecimal FALLBACK_FUNERAL_MAXIMUM = BigDecimal.ZERO;
    private static final int FALLBACK_ELIGIBLE_PERIOD_DAYS = 90;

    private final DeathDonationConfigRepository configRepository;
    private final DeathDonationEligibilityTierRepository tierRepository;
    private final CauseOfDeathRepository causeOfDeathRepository;
    private final FinanceDeathDonationClient financeClient;

    public DeathDonationEntitlementService(
            DeathDonationConfigRepository configRepository,
            DeathDonationEligibilityTierRepository tierRepository,
            CauseOfDeathRepository causeOfDeathRepository,
            FinanceDeathDonationClient financeClient
    ) {
        this.configRepository = configRepository;
        this.tierRepository = tierRepository;
        this.causeOfDeathRepository = causeOfDeathRepository;
        this.financeClient = financeClient;
    }

    /**
     * Fills in the Finance-owned figures for a record that has none yet, then
     * calculates. Called after the first save, which is when the SRS says the
     * amounts appear: "Once the record is saved and retrieved back, the interface
     * will display the Death Donation amounts."
     *
     * Values the user has already edited are never overwritten by a later Finance
     * reading - that would silently undo a correction someone made deliberately.
     */
    @Transactional
    public void populateFromFinance(DonationEntitlementTarget record) {
        DeathDonationFinanceSnapshotDTO snapshot =
                financeClient.fetchSnapshot(record.getMember().getMemberId());

        if (!Boolean.TRUE.equals(record.getMonthsRemittedEdited())) {
            record.setMonthsRemitted(snapshot.getMonthsRemitted());
        }
        if (!Boolean.TRUE.equals(record.getReceivedPast12MonthsEdited())) {
            record.setReceivedPast12Months(snapshot.getReceivedPast12Months());
        }

        record.setFuneralAccountNo(snapshot.getFuneralAccountNo());
        record.setFuneralAccountCredited(snapshot.getFuneralAccountCredited());
        record.setFuneralAccountMaximum(configValue(
                DeathDonationConfigSeeder.FUNERAL_ACCOUNT_MAXIMUM, FALLBACK_FUNERAL_MAXIMUM));

        recalculate(record);
    }

    /**
     * Recalculates every derived figure from whatever the three editable inputs
     * currently hold. This is what the SRS refresh button does: "If the user
     * change this value, by clicking on the refresh button next to the field, the
     * rest of the values will be recalculated."
     */
    @Transactional
    public void recalculate(DonationEntitlementTarget record) {
        BigDecimal maximum = resolveMaximum(record);
        BigDecimal rate = resolveTierRate(record.getMonthsRemitted());
        BigDecimal eligible = money(maximum.multiply(rate).divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP));

        boolean hasFuneralAccount = record.getFuneralAccountNo() != null
                && !record.getFuneralAccountNo().isBlank();

        BigDecimal multiplier = hasFuneralAccount
                ? configValue(DeathDonationConfigSeeder.FUNERAL_ACCOUNT_MULTIPLIER, FALLBACK_MULTIPLIER)
                : BigDecimal.ONE;

        maximum = money(maximum.multiply(multiplier));
        eligible = money(eligible.multiply(multiplier));

        BigDecimal received = orZero(record.getReceivedPast12Months());
        BigDecimal entitled = money(eligible.subtract(received).max(BigDecimal.ZERO));

        BigDecimal credited;
        if (hasFuneralAccount) {
            BigDecimal funeralMaximum = orZero(record.getFuneralAccountMaximum());
            BigDecimal alreadyCredited = orZero(record.getFuneralAccountCredited());
            BigDecimal headroom = funeralMaximum.subtract(alreadyCredited).max(BigDecimal.ZERO);

            if (Boolean.TRUE.equals(record.getCreditedToSpecialFixedEdited())
                    && record.getCreditedToSpecialFixedAccount() != null) {
                // An operator override still cannot exceed what is actually
                // available, or the disburse amount would go negative.
                credited = orZero(record.getCreditedToSpecialFixedAccount()).min(entitled).max(BigDecimal.ZERO);
            } else {
                credited = entitled.min(headroom);
            }
        } else {
            credited = BigDecimal.ZERO;
        }

        record.setMaximumDonationAmount(maximum);
        record.setEligibleDonationAmount(eligible);
        record.setDonationMultiplierApplied(multiplier);
        record.setCreditedToSpecialFixedAccount(money(credited));
        record.setDisburseDonationAmount(money(entitled.subtract(credited).max(BigDecimal.ZERO)));

        // Keep the legacy single-amount column in step, so anything still reading
        // it sees the payable figure rather than a stale hand-typed number.
        record.setDeathDonationAmount(record.getDisburseDonationAmount());
    }

    /**
     * Applies the three operator-editable inputs, flagging each one that actually
     * differs from what is stored, then recalculates. A null means "leave it as
     * it is", not "clear it".
     */
    @Transactional
    public void applyOverrides(
            DonationEntitlementTarget record,
            Integer monthsRemitted,
            BigDecimal receivedPast12Months,
            BigDecimal creditedToSpecialFixedAccount
    ) {
        if (monthsRemitted != null && !monthsRemitted.equals(record.getMonthsRemitted())) {
            record.setMonthsRemitted(monthsRemitted);
            record.setMonthsRemittedEdited(true);
        }

        if (receivedPast12Months != null
                && compare(receivedPast12Months, record.getReceivedPast12Months()) != 0) {
            record.setReceivedPast12Months(receivedPast12Months);
            record.setReceivedPast12MonthsEdited(true);
        }

        if (creditedToSpecialFixedAccount != null
                && compare(creditedToSpecialFixedAccount, record.getCreditedToSpecialFixedAccount()) != 0) {
            record.setCreditedToSpecialFixedAccount(creditedToSpecialFixedAccount);
            record.setCreditedToSpecialFixedEdited(true);
        }

        recalculate(record);
    }

    /**
     * MMT20: "if the requested date is exceeding the pre-defined eligible period
     * based on the deceased date, a warning will be displayed in the Concerns
     * Identified section."
     *
     * @return the warning text, or null when the record was informed in time
     */
    public String buildEligiblePeriodWarning(MemberDeathRecord record) {
        return buildEligiblePeriodWarning(
                record.getDeceasedDate(), record.getInformedDate(), "The death was informed");
    }

    /**
     * The same rule for a Death Donation Request (SRS 2.2.1 / 2.2.3): "If the
     * Requested Date is exceeding the defined period of the Deceased Date [...] a
     * warning message will display in the Concerns Identified section".
     *
     * Both callers read the one configured limit, which is what stopped the
     * donation flow drifting onto its own hardcoded three-month window.
     *
     * @return the warning text, or null when the request was raised in time
     */
    public String buildEligiblePeriodWarning(LocalDate deceasedDate, LocalDate requestedDate) {
        return buildEligiblePeriodWarning(deceasedDate, requestedDate, "The donation was requested");
    }

    /** How many days the configured eligible period allows. */
    public int eligiblePeriodDays() {
        return configValue(
                DeathDonationConfigSeeder.DONATION_ELIGIBLE_PERIOD_DAYS,
                BigDecimal.valueOf(FALLBACK_ELIGIBLE_PERIOD_DAYS)).intValue();
    }

    private String buildEligiblePeriodWarning(
            LocalDate deceasedDate,
            LocalDate comparisonDate,
            String subject
    ) {
        if (deceasedDate == null || comparisonDate == null) {
            return null;
        }

        int limit = eligiblePeriodDays();

        long elapsed = ChronoUnit.DAYS.between(deceasedDate, comparisonDate);
        if (elapsed <= limit) {
            return null;
        }

        return subject + " " + elapsed + " days after the deceased date, exceeding the "
                + limit + " day eligible period. Please review before proceeding.";
    }

    /**
     * The cause-of-death override wins outright where it is configured: the SRS
     * says that value is "selected ignoring the general death donation value
     * configured in the system".
     */
    private BigDecimal resolveMaximum(DonationEntitlementTarget record) {
        String causeName = record.getCauseOfDeath();
        if (causeName != null && !causeName.isBlank()) {
            CauseOfDeath cause = causeOfDeathRepository.findAll().stream()
                    .filter(item -> causeName.equalsIgnoreCase(item.getName()))
                    .findFirst()
                    .orElse(null);

            if (cause != null && cause.getMaxDeathDonationAmount() != null) {
                return cause.getMaxDeathDonationAmount();
            }
        }

        return configValue(DeathDonationConfigSeeder.DEFAULT_MAX_DONATION, FALLBACK_MAX_DONATION);
    }

    /** The highest band the member has actually reached, as a percentage. */
    private BigDecimal resolveTierRate(Integer monthsRemitted) {
        int months = monthsRemitted != null ? monthsRemitted : 0;

        List<DeathDonationEligibilityTier> tiers = tierRepository.findAllByOrderByMinMonthsDesc();
        for (DeathDonationEligibilityTier tier : tiers) {
            if (tier.getMinMonths() != null && months >= tier.getMinMonths()) {
                return orZero(tier.getPercentage());
            }
        }

        // No tiers configured at all. Zero rather than full entitlement: an
        // unseeded master must not quietly authorise the maximum payout.
        return BigDecimal.ZERO;
    }

    private BigDecimal configValue(String key, BigDecimal fallback) {
        return configRepository.findByConfigKey(key)
                .map(DeathDonationConfig::getConfigValue)
                .orElse(fallback);
    }

    private BigDecimal money(BigDecimal value) {
        return orZero(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private int compare(BigDecimal left, BigDecimal right) {
        return orZero(left).compareTo(orZero(right));
    }
}

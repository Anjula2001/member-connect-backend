package com.memberconnect.backend.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.DeathDonationConfig;
import com.memberconnect.backend.model.DeathDonationEligibilityTier;
import com.memberconnect.backend.repository.DeathDonationConfigRepository;
import com.memberconnect.backend.repository.DeathDonationEligibilityTierRepository;

/**
 * Seeds the Death Donation entitlement rules the SRS (4.2.3) assumes already
 * exist as "configured value(s) in the system".
 *
 * Like TerminationDocumentSeeder, the guard is scoped to this seeder's own rows
 * rather than a whole-table count, so an administrator who edits these values
 * keeps their changes - the seeder only ever fills a completely empty set.
 *
 * The amounts below are placeholders. They become live master data that recorded
 * entitlements are calculated from, so confirm them against the client's figures
 * before going to production rather than after records exist.
 */
@Component
@Order(4)
public class DeathDonationConfigSeeder implements CommandLineRunner {

    /** General maximum, used when the Cause of Death carries no override. */
    public static final String DEFAULT_MAX_DONATION = "DEFAULT_MAX_DONATION";

    /**
     * SRS: when the member holds a Special Fixed Account for Funerals, the
     * maximum and eligible amounts "will be multiplied by a pre-defined
     * multiplier in the system. (by default it will '2')".
     */
    public static final String FUNERAL_ACCOUNT_MULTIPLIER = "FUNERAL_ACCOUNT_MULTIPLIER";

    /** Cap on the Special Fixed Account for Funerals, excluding interest. */
    public static final String FUNERAL_ACCOUNT_MAXIMUM = "FUNERAL_ACCOUNT_MAXIMUM";

    /**
     * MMT20: "if the requested date is exceeding the pre-defined eligible period
     * based on the deceased date, a warning will be displayed in the Concerns
     * Identified section."
     */
    public static final String DONATION_ELIGIBLE_PERIOD_DAYS = "DONATION_ELIGIBLE_PERIOD_DAYS";

    private static final List<String> CONFIG_KEYS = List.of(
            DEFAULT_MAX_DONATION,
            FUNERAL_ACCOUNT_MULTIPLIER,
            FUNERAL_ACCOUNT_MAXIMUM,
            DONATION_ELIGIBLE_PERIOD_DAYS);

    private final DeathDonationConfigRepository configRepository;
    private final DeathDonationEligibilityTierRepository tierRepository;

    public DeathDonationConfigSeeder(
            DeathDonationConfigRepository configRepository,
            DeathDonationEligibilityTierRepository tierRepository
    ) {
        this.configRepository = configRepository;
        this.tierRepository = tierRepository;
    }

    @Override
    public void run(String... args) {
        seedConfig();
        seedTiers();
    }

    private void seedConfig() {
        if (!configRepository.findByConfigKeyIn(CONFIG_KEYS).isEmpty()) {
            return;
        }

        List<DeathDonationConfig> values = new ArrayList<>();
        values.add(create(DEFAULT_MAX_DONATION, new BigDecimal("100000.00"),
                "General Maximum Death Donation Amount, when the cause of death has no override"));
        values.add(create(FUNERAL_ACCOUNT_MULTIPLIER, new BigDecimal("2.00"),
                "Multiplier applied to the maximum and eligible amounts when a Special Fixed Account for Funerals exists"));
        values.add(create(FUNERAL_ACCOUNT_MAXIMUM, new BigDecimal("200000.00"),
                "Maximum credit the Special Fixed Account for Funerals may hold, excluding interest"));
        values.add(create(DONATION_ELIGIBLE_PERIOD_DAYS, new BigDecimal("90"),
                "Days after the deceased date within which the death should be informed"));

        configRepository.saveAll(values);
        System.out.println("Seeded " + values.size() + " death donation config values.");
    }

    private void seedTiers() {
        if (!tierRepository.findAll().isEmpty()) {
            return;
        }

        List<DeathDonationEligibilityTier> tiers = new ArrayList<>();
        tiers.add(createTier(0, new BigDecimal("0.00")));
        tiers.add(createTier(12, new BigDecimal("25.00")));
        tiers.add(createTier(24, new BigDecimal("50.00")));
        tiers.add(createTier(60, new BigDecimal("100.00")));

        tierRepository.saveAll(tiers);
        System.out.println("Seeded " + tiers.size() + " death donation eligibility tiers.");
    }

    private DeathDonationConfig create(String key, BigDecimal value, String description) {
        DeathDonationConfig config = new DeathDonationConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(description);
        return config;
    }

    private DeathDonationEligibilityTier createTier(int minMonths, BigDecimal percentage) {
        DeathDonationEligibilityTier tier = new DeathDonationEligibilityTier();
        tier.setMinMonths(minMonths);
        tier.setPercentage(percentage);
        return tier;
    }
}

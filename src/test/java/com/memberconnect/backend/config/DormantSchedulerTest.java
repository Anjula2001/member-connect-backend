package com.memberconnect.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.memberconnect.backend.model.DormantConfig;
import com.memberconnect.backend.service.DormantMembershipService;

/**
 * The MMD10 schedule, and specifically its catch-up behaviour.
 *
 * The old scheduler compared the clock to the configured day/hour/minute and ran
 * only on an exact match - a one-minute window per month. A restart spanning that
 * minute meant the month's run never happened, silently. These tests pin the
 * replacement rule: has this month's moment passed, and have we not run since?
 *
 * isDueThisMonth is exercised directly rather than through @Scheduled, because
 * the alternative is waiting for a real clock to reach the 25th.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DormantSchedulerTest {

    @Mock private DormantMembershipService dormantService;

    private final DormantScheduler scheduler = new DormantScheduler(null);

    private DormantConfig config(int day, int hour, int minute, LocalDate lastRun) {
        DormantConfig config = new DormantConfig();
        config.setScheduleDayOfMonth(day);
        config.setScheduleHour(hour);
        config.setScheduleMinute(minute);
        config.setEnabled(true);
        config.setLastRunOn(lastRun);
        return config;
    }

    @Test
    void doesNotRunBeforeTheScheduledMomentInTheMonth() {
        assertThat(scheduler.isDueThisMonth(
                config(25, 0, 0, null),
                LocalDateTime.of(2026, 8, 24, 23, 59)))
                .isFalse();
    }

    @Test
    void runsOnceTheScheduledMomentHasArrived() {
        assertThat(scheduler.isDueThisMonth(
                config(25, 0, 0, null),
                LocalDateTime.of(2026, 8, 25, 0, 0)))
                .isTrue();
    }

    /**
     * The whole point of the rewrite: the application was down at midnight on the
     * 25th, so the run is picked up on the 26th rather than lost for the month.
     */
    @Test
    void catchesUpOnARunItMissedEarlierInTheMonth() {
        assertThat(scheduler.isDueThisMonth(
                config(25, 0, 0, LocalDate.of(2026, 7, 25)),
                LocalDateTime.of(2026, 8, 26, 9, 30)))
                .isTrue();
    }

    @Test
    void doesNotRunTwiceInTheSameMonth() {
        assertThat(scheduler.isDueThisMonth(
                config(25, 0, 0, LocalDate.of(2026, 8, 25)),
                LocalDateTime.of(2026, 8, 25, 0, 5)))
                .isFalse();
    }

    @Test
    void staysQuietForTheRestOfTheMonthAfterRunning() {
        assertThat(scheduler.isDueThisMonth(
                config(25, 0, 0, LocalDate.of(2026, 8, 25)),
                LocalDateTime.of(2026, 8, 31, 23, 59)))
                .isFalse();
    }

    @Test
    void runsAgainTheFollowingMonth() {
        assertThat(scheduler.isDueThisMonth(
                config(25, 0, 0, LocalDate.of(2026, 8, 25)),
                LocalDateTime.of(2026, 9, 25, 0, 0)))
                .isTrue();
    }

    /**
     * A manual run on the 3rd must not cancel the scheduled run on the 25th -
     * which is why lastRunOn is compared against this month's scheduled date
     * rather than the start of the month.
     */
    @Test
    void aManualRunEarlyInTheMonthDoesNotCancelTheScheduledOne() {
        assertThat(scheduler.isDueThisMonth(
                config(25, 0, 0, LocalDate.of(2026, 8, 3)),
                LocalDateTime.of(2026, 8, 25, 0, 0)))
                .isTrue();
    }

    @Test
    void honoursANonMidnightSchedule() {
        DormantConfig config = config(10, 14, 30, null);

        assertThat(scheduler.isDueThisMonth(config, LocalDateTime.of(2026, 8, 10, 14, 29))).isFalse();
        assertThat(scheduler.isDueThisMonth(config, LocalDateTime.of(2026, 8, 10, 14, 30))).isTrue();
    }
}

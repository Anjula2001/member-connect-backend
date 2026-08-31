package com.memberconnect.backend.config;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.DormantConfig;
import com.memberconnect.backend.service.DormantMembershipService;

/**
 * Runs the Dormant Membership Identification process automatically at the
 * configured day-of-month, hour and minute (SRS MMD10).
 *
 * <h2>Why this catches up instead of matching the clock</h2>
 *
 * The original version fired every minute and ran the process only when the
 * current day, hour and minute all equalled the configuration. That is a
 * one-minute window per month, and missing it meant missing the month: a restart
 * spanning 00:00 on the 25th, a garbage collection pause, or the single-threaded
 * default scheduler being busy with anything else, and the run simply never
 * happened - silently, with nothing to indicate it had been skipped.
 *
 * This version asks a different question. Not "is it exactly the scheduled
 * moment?" but "has the scheduled moment for this month passed, and have we not
 * run since?" - which is answered by DormantConfig.lastRunOn. A missed window is
 * picked up on the next tick rather than lost, and the process still runs exactly
 * once per month because lastRunOn is stamped the moment it completes.
 *
 * Polling every five minutes rather than every minute follows from that: with
 * catch-up there is nothing to be gained from a tighter loop, and the run is
 * idempotent anyway.
 *
 * <h2>Known limitation</h2>
 *
 * This assumes a single application instance. Two instances would each see
 * lastRunOn as stale and could both start a run. Guarding that properly needs a
 * distributed lock (ShedLock or equivalent), which this project does not have as
 * a dependency; the identification process is idempotent, so the cost of a double
 * run is duplicated work rather than incorrect data.
 */
@Component
public class DormantScheduler {

    private final DormantMembershipService dormantService;

    public DormantScheduler(DormantMembershipService dormantService) {
        this.dormantService = dormantService;
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void runScheduledIdentification() {
        DormantConfig config = dormantService.getConfig();

        if (config.getEnabled() == null || !config.getEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        if (!isDueThisMonth(config, now)) {
            return;
        }

        System.out.println("Scheduled dormant identification triggered at " + now);
        dormantService.runIdentification();
    }

    /**
     * True when this month's scheduled moment has passed and no run has been
     * recorded since the start of this month.
     *
     * Package-private rather than private so DormantSchedulerTest can exercise
     * the calendar logic without waiting for a real clock to reach the 25th.
     */
    boolean isDueThisMonth(DormantConfig config, LocalDateTime now) {
        LocalDateTime scheduledThisMonth = now
                .withDayOfMonth(config.getScheduleDayOfMonth())
                .withHour(config.getScheduleHour())
                .withMinute(config.getScheduleMinute())
                .withSecond(0)
                .withNano(0);

        if (now.isBefore(scheduledThisMonth)) {
            return false;
        }

        LocalDate lastRun = config.getLastRunOn();

        if (lastRun == null) {
            return true;
        }

        // Already run at some point on or after this month's scheduled day, so
        // this month is done. Comparing against the scheduled date rather than
        // the start of the month is what stops a manual run early in the month
        // from cancelling the scheduled one.
        return lastRun.isBefore(scheduledThisMonth.toLocalDate());
    }
}

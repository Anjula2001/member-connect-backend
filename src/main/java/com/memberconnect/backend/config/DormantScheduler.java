package com.memberconnect.backend.config;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.DormantConfig;
import com.memberconnect.backend.service.DormantMembershipService;

/**
 * Runs the Dormant Membership Identification process automatically at the
 * configured day-of-month, hour and minute. The task fires once a minute and
 * only executes the process when the current time matches the configuration.
 */
@Component
public class DormantScheduler {

    private final DormantMembershipService dormantService;

    public DormantScheduler(DormantMembershipService dormantService) {
        this.dormantService = dormantService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void runScheduledIdentification() {
        DormantConfig config = dormantService.getConfig();
        if (config.getEnabled() == null || !config.getEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean matches = now.getDayOfMonth() == config.getScheduleDayOfMonth()
                && now.getHour() == config.getScheduleHour()
                && now.getMinute() == config.getScheduleMinute();
        if (matches) {
            System.out.println("Scheduled dormant identification triggered at " + now);
            dormantService.runIdentification();
        }
    }
}

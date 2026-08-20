package com.memberconnect.backend.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DormantConfigDTO {
    private Integer dormantPeriodMonths;
    private Integer scheduleDayOfMonth;
    private Integer scheduleHour;
    private Integer scheduleMinute;
    private Boolean enabled;

    // Read-only. Shown on the screen as "Last run", and used by nothing else.
    private LocalDate lastRunOn;
    private Integer lastRunSelectedCount;
    private Integer lastRunClearedCount;
}
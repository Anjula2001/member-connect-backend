package com.memberconnect.backend.dto;

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
}

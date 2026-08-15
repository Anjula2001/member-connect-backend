package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Single-row configuration for the Dormant Membership Identification process.
 */
@Getter
@Setter
@Entity
@Table(name = "dormant_config")
public class DormantConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Number of months of inactivity after which a member is considered dormant.
    @Column(name = "dormant_period_months", nullable = false)
    private Integer dormantPeriodMonths = 12;

    // Day of the month on which the scheduled process runs (1-28).
    @Column(name = "schedule_day_of_month", nullable = false)
    private Integer scheduleDayOfMonth = 25;

    // Hour of the day (0-23) at which the scheduled process runs.
    @Column(name = "schedule_hour", nullable = false)
    private Integer scheduleHour = 0;

    // Minute of the hour (0-59) at which the scheduled process runs.
    @Column(name = "schedule_minute", nullable = false)
    private Integer scheduleMinute = 0;

    // When false the scheduled run is skipped (manual run still works).
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}

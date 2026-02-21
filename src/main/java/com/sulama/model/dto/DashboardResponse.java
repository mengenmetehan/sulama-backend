package com.sulama.model.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private DeviceStatusResponse currentStatus;
    private long todayWaterUsedLiters;
    private long totalMotorRunMinutesToday;
    private int activeScheduleCount;
    private LocalDateTime serverTime;
}

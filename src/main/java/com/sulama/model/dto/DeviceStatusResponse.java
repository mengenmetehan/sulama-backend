package com.sulama.model.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceStatusResponse {
    private boolean motorRunning;
    private Integer soilMoisture;
    private BigDecimal waterUsedLiters;
    private BigDecimal temperature;
    private Integer wifiRssi;
    private Long uptimeSeconds;
    private boolean deviceOnline;
    private LocalDateTime lastSeen;
}

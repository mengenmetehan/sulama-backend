package com.sulama.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_status",
       indexes = @Index(name = "idx_device_status_device_reported",
                        columnList = "device_id, reported_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 50)
    @Builder.Default
    private String deviceId = "ESP32_MAIN";

    @Column(name = "motor_running", nullable = false)
    @Builder.Default
    private Boolean motorRunning = false;

    @Column(name = "soil_moisture")
    private Integer soilMoisture;

    @Column(name = "water_used_liters", precision = 10, scale = 2)
    private BigDecimal waterUsedLiters;

    @Column(name = "temperature", precision = 5, scale = 2)
    private BigDecimal temperature;

    @Column(name = "wifi_rssi")
    private Integer wifiRssi;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (reportedAt == null) reportedAt = LocalDateTime.now();
    }
}

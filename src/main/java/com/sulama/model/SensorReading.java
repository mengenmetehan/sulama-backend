package com.sulama.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sensor_reading",
       indexes = {
           @Index(name = "idx_sensor_reading_recorded", columnList = "recorded_at"),
           @Index(name = "idx_sensor_reading_device_recorded", columnList = "device_id, recorded_at")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 50)
    @Builder.Default
    private String deviceId = "ESP32_MAIN";

    @Column(name = "soil_moisture")
    private Integer soilMoisture;

    @Column(name = "temperature", precision = 5, scale = 2)
    private BigDecimal temperature;

    @Column(name = "water_flow_lpm", precision = 8, scale = 2)
    private BigDecimal waterFlowLpm;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private LocalDateTime recordedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) recordedAt = LocalDateTime.now();
    }
}

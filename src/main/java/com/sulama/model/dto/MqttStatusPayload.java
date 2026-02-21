package com.sulama.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

/**
 * ESP32'den MQTT üzerinden gelen status payload'ı.
 * ESP32 tarafında snake_case kullanılıyor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MqttStatusPayload {

    private boolean motor;

    @JsonProperty("soil_moisture")
    private Integer soilMoisture;

    @JsonProperty("water_used_liters")
    private BigDecimal waterUsedLiters;

    private BigDecimal temperature;

    @JsonProperty("wifi_rssi")
    private Integer wifiRssi;

    @JsonProperty("uptime_seconds")
    private Long uptimeSeconds;
}

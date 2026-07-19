package com.sulama.model.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bir güne ait ortalama sensör değerleri.
 * Alan adları SensorReading ile aynı tutuldu — mobil uygulama değişmeden okuyabilsin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailySensorReading {

    private LocalDate recordedAt;

    /** Günlük ortalama hava nemi (%) */
    private Integer soilMoisture;

    /** Günlük ortalama sıcaklık (°C) */
    private BigDecimal temperature;
}

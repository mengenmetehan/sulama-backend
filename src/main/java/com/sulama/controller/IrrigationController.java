package com.sulama.controller;

import com.sulama.model.MotorLog;
import com.sulama.model.SensorReading;
import com.sulama.model.enums.MotorAction;
import com.sulama.model.enums.MotorSource;
import com.sulama.model.dto.*;
import com.sulama.repository.MotorLogRepository;
import com.sulama.repository.SensorReadingRepository;
import com.sulama.service.IrrigationService;
import com.sulama.service.MqttService;
import com.sulama.service.SchedulerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/irrigation")
@RequiredArgsConstructor
public class IrrigationController {

    private final IrrigationService irrigationService;
    private final MqttService mqttService;
    private final SchedulerService schedulerService;
    private final MotorLogRepository motorLogRepo;
    private final SensorReadingRepository sensorReadingRepo;

    // ==================== MOTOR KONTROL ====================

    /**
     * Motor aç/kapat
     * POST /api/irrigation/motor/on
     * POST /api/irrigation/motor/off
     */
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/motor/{action}")
    public ResponseEntity<MotorCommandResponse> controlMotor(
            @PathVariable String action) {

        if (!"on".equalsIgnoreCase(action) && !"off".equalsIgnoreCase(action)) {
            return ResponseEntity.badRequest().build();
        }

        MotorCommandResponse response = irrigationService.controlMotor(
                action.toLowerCase(), MotorSource.MANUAL);

        return ResponseEntity.ok(response);
    }

    // ==================== DURUM ====================

    /**
     * Cihaz anlık durumu
     * GET /api/irrigation/status
     */
    @GetMapping("/status")
    public ResponseEntity<DeviceStatusResponse> getStatus() {
        return ResponseEntity.ok(mqttService.getLastStatus());
    }

    /**
     * Dashboard özet
     * GET /api/irrigation/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard() {
        DeviceStatusResponse status = mqttService.getLastStatus();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        // Bugün başlayıp tamamlanan oturumlar
        long completedMinutes = motorLogRepo.findRecentLogs(todayStart).stream()
                .filter(l -> l.getStartedAt() != null && l.getStoppedAt() != null)
                .mapToLong(l -> java.time.Duration.between(l.getStartedAt(), l.getStoppedAt()).toMinutes())
                .sum();

        // Aktif oturum — gece yarısı öncesi başlamış olabilir, sadece bugünkü kısmı say
        long activeMinutes = motorLogRepo.findActiveMotorSession()
                .filter(a -> a.getStartedAt() != null)
                .map(a -> {
                    LocalDateTime from = a.getStartedAt().isBefore(todayStart) ? todayStart : a.getStartedAt();
                    return java.time.Duration.between(from, now).toMinutes();
                })
                .orElse(0L);

        long totalMinutes = completedMinutes + activeMinutes;

        int activeCount = (int) irrigationService.getAllSchedules().stream()
                .filter(ScheduleResponse::isEnabled)
                .count();

        DashboardResponse dashboard = DashboardResponse.builder()
                .currentStatus(status)
                .todayWaterUsedLiters(status.getWaterUsedLiters() != null
                        ? status.getWaterUsedLiters().longValue() : 0)
                .totalMotorRunMinutesToday(totalMinutes)
                .activeScheduleCount(activeCount)
                .serverTime(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(dashboard);
    }

    // ==================== ZAMANLAYICI ====================

    /**
     * Tüm zamanlayıcılar
     * GET /api/irrigation/schedules
     */
    @GetMapping("/schedules")
    public ResponseEntity<List<ScheduleResponse>> getSchedules() {
        return ResponseEntity.ok(irrigationService.getAllSchedules());
    }

    /**
     * Yeni zamanlayıcı
     * POST /api/irrigation/schedules
     */
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/schedules")
    public ResponseEntity<ScheduleResponse> createSchedule(
            @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(irrigationService.createSchedule(request));
    }

    /**
     * Zamanlayıcı güncelle
     * PUT /api/irrigation/schedules/{id}
     */
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/schedules/{id}")
    public ResponseEntity<ScheduleResponse> updateSchedule(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(irrigationService.updateSchedule(id, request));
    }

    /**
     * Zamanlayıcı sil
     * DELETE /api/irrigation/schedules/{id}
     */
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/schedules/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id) {
        irrigationService.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Zamanlayıcı aktif/pasif toggle
     * POST /api/irrigation/schedules/{id}/toggle
     */
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/schedules/{id}/toggle")
    public ResponseEntity<ScheduleResponse> toggleSchedule(@PathVariable Long id) {
        return ResponseEntity.ok(irrigationService.toggleSchedule(id));
    }

    // ==================== SENSÖR VERİLERİ ====================

    /**
     * Sensör okuma geçmişi — günlük ortalamalar
     * GET /api/irrigation/sensors?days=30
     */
    @GetMapping("/sensors")
    public ResponseEntity<List<DailySensorReading>> getSensorData(
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime since = LocalDate.now().minusDays(Math.min(days, 365) - 1L).atStartOfDay();
        List<SensorReading> readings = sensorReadingRepo.findRecent("ESP32_MAIN", since);

        Map<LocalDate, List<SensorReading>> byDay = readings.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> r.getRecordedAt().toLocalDate(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.toList()));

        List<DailySensorReading> daily = byDay.entrySet().stream()
                .map(e -> {
                    List<SensorReading> dayReadings = e.getValue();

                    java.util.OptionalDouble avgMoisture = dayReadings.stream()
                            .filter(r -> r.getSoilMoisture() != null)
                            .mapToInt(SensorReading::getSoilMoisture)
                            .average();

                    java.util.OptionalDouble avgTemp = dayReadings.stream()
                            .filter(r -> r.getTemperature() != null)
                            .mapToDouble(r -> r.getTemperature().doubleValue())
                            .average();

                    return DailySensorReading.builder()
                            .recordedAt(e.getKey())
                            .soilMoisture(avgMoisture.isPresent()
                                    ? (int) Math.round(avgMoisture.getAsDouble()) : null)
                            .temperature(avgTemp.isPresent()
                                    ? java.math.BigDecimal.valueOf(avgTemp.getAsDouble())
                                            .setScale(1, java.math.RoundingMode.HALF_UP) : null)
                            .build();
                })
                .toList();

        return ResponseEntity.ok(daily);
    }

    // ==================== MOTOR LOG ====================

    /**
     * Motor çalışma geçmişi
     * GET /api/irrigation/logs?days=7
     */
    @GetMapping("/logs")
    public ResponseEntity<?> getMotorLogs(
            @RequestParam(defaultValue = "7") int days) {

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return ResponseEntity.ok(motorLogRepo.findRecentLogs(since));
    }

    // ==================== TEST ====================

    /**
     * TEST — MQTT disconnect (isOffline=true) senaryosunu zorla tetikler.
     * Kayıtlı tüm cihazlara "Cihaz Çevrimdışı" FCM bildirimi gönderir.
     * POST /api/irrigation/test/device-offline
     */
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/test/device-offline")
    public ResponseEntity<Map<String, Object>> testDeviceOffline() {
        int tokenCount = schedulerService.triggerOfflineNotificationForTest();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cihaz çevrimdışı bildirimi gönderildi",
                "tokenCount", tokenCount
        ));
    }

    // ==================== SAĞLIK KONTROLÜ ====================

    /**
     * GET /api/irrigation/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        DeviceStatusResponse status = mqttService.getLastStatus();

        return ResponseEntity.ok(Map.of(
                "api", "ok",
                "mqtt_connected", mqttService.isConnected(),
                "device_online", status.isDeviceOnline(),
                "motor_running", status.isMotorRunning(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}

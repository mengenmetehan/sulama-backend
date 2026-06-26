package com.sulama.controller;

import com.sulama.model.SensorReading;
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
        var todayLogs = motorLogRepo.findRecentLogs(todayStart);

        long totalMinutes = todayLogs.stream()
                .filter(l -> l.getStartedAt() != null && l.getStoppedAt() != null)
                .mapToLong(l -> java.time.Duration.between(
                        l.getStartedAt(), l.getStoppedAt()).toMinutes())
                .sum();

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
     * Sensör okuma geçmişi
     * GET /api/irrigation/sensors?hours=24
     */
    @GetMapping("/sensors")
    public ResponseEntity<List<SensorReading>> getSensorData(
            @RequestParam(defaultValue = "24") int hours) {

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return ResponseEntity.ok(sensorReadingRepo.findRecent("ESP32_MAIN", since));
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

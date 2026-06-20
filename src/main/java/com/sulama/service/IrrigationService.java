package com.sulama.service;

import com.sulama.config.IrrigationProperties;
import com.sulama.model.IrrigationSchedule;
import com.sulama.model.MotorLog;
import com.sulama.model.enums.MotorAction;
import com.sulama.model.enums.MotorSource;
import com.sulama.model.dto.MotorCommandResponse;
import com.sulama.model.dto.ScheduleRequest;
import com.sulama.model.dto.ScheduleResponse;
import com.sulama.repository.MotorLogRepository;
import com.sulama.repository.ScheduleRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class IrrigationService {

    private final MqttService mqttService;
    private final ScheduleRepository scheduleRepo;
    private final MotorLogRepository motorLogRepo;
    private final IrrigationProperties irrigationProperties;

    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "motor-heartbeat");
                t.setDaemon(true);
                return t;
            });
    private volatile ScheduledFuture<?> motorHeartbeat;

    @PreDestroy
    public void shutdown() {
        stopHeartbeat();
        heartbeatExecutor.shutdownNow();
    }

    // ============================================================
    // MOTOR KONTROL
    // ============================================================

    @Transactional
    public MotorCommandResponse controlMotor(String action, MotorSource source) {
        return controlMotor(action, source, null);
    }

    @Transactional
    public MotorCommandResponse controlMotor(String action, MotorSource source, Long scheduleId) {
        boolean turnOn = "on".equalsIgnoreCase(action);

        if (turnOn) {
            startHeartbeat();
        } else {
            stopHeartbeat();
            mqttService.sendCommand("MOTOR_OFF");
        }

        // Log kaydet
        if (turnOn) {
            MotorLog motorLog = MotorLog.builder()
                    .action(MotorAction.ON)
                    .source(source)
                    .scheduleId(scheduleId)
                    .startedAt(LocalDateTime.now())
                    .build();
            motorLogRepo.save(motorLog);
            log.info("Motor AÇILDI — Kaynak: {}, Zamanlayıcı: {}", source, scheduleId);
        } else {
            // Aktif oturumu kapat
            motorLogRepo.findActiveMotorSession().ifPresent(session -> {
                session.setAction(MotorAction.OFF);
                session.setStoppedAt(LocalDateTime.now());
                motorLogRepo.save(session);
                log.info("Motor KAPATILDI — Çalışma süresi: {} dk", session.getRuntimeMinutes());
            });
        }

        return MotorCommandResponse.builder()
                .status("command_sent")
                .action(action)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ============================================================
    // ZAMANLAYICI YÖNETİMİ
    // ============================================================

    public List<ScheduleResponse> getAllSchedules() {
        return scheduleRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toScheduleResponse)
                .toList();
    }

    @Transactional
    public ScheduleResponse createSchedule(ScheduleRequest request) {
        IrrigationSchedule schedule = IrrigationSchedule.builder()
                .name(request.getName())
                .cronExpression(request.getCronExpression())
                .durationMinutes(request.getDurationMinutes())
                .zone(request.getZone() != null ? request.getZone() : "ALL")
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        schedule = scheduleRepo.save(schedule);
        log.info("Zamanlayıcı oluşturuldu: {} — {}", schedule.getName(), schedule.getCronExpression());

        return toScheduleResponse(schedule);
    }

    @Transactional
    public ScheduleResponse updateSchedule(Long id, ScheduleRequest request) {
        IrrigationSchedule schedule = findScheduleOrThrow(id);

        if (request.getName() != null) schedule.setName(request.getName());
        if (request.getCronExpression() != null) schedule.setCronExpression(request.getCronExpression());
        if (request.getDurationMinutes() != null) schedule.setDurationMinutes(request.getDurationMinutes());
        if (request.getZone() != null) schedule.setZone(request.getZone());
        if (request.getEnabled() != null) schedule.setEnabled(request.getEnabled());

        schedule = scheduleRepo.save(schedule);
        log.info("Zamanlayıcı güncellendi: {}", schedule.getName());

        return toScheduleResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(Long id) {
        if (!scheduleRepo.existsById(id)) {
            throw new IllegalArgumentException("Zamanlayıcı bulunamadı: " + id);
        }
        scheduleRepo.deleteById(id);
        log.info("Zamanlayıcı silindi: {}", id);
    }

    @Transactional
    public ScheduleResponse toggleSchedule(Long id) {
        IrrigationSchedule schedule = findScheduleOrThrow(id);
        schedule.setEnabled(!schedule.getEnabled());
        schedule = scheduleRepo.save(schedule);

        log.info("Zamanlayıcı {} — {}", schedule.getName(),
                schedule.getEnabled() ? "AKTİF" : "DEVRE DIŞI");

        return toScheduleResponse(schedule);
    }

    // ============================================================
    // GÜVENLİK
    // ============================================================

    /**
     * Motor çok uzun süredir çalışıyorsa otomatik kapat
     */
    public void checkMotorSafety() {
        motorLogRepo.findActiveMotorSession().ifPresent(session -> {
            long runtimeMinutes = Duration.between(
                    session.getStartedAt(), LocalDateTime.now()).toMinutes();

            int maxRuntime = irrigationProperties.getMotor().getMaxRuntimeMinutes();
            if (runtimeMinutes >= maxRuntime) {
                log.warn("GÜVENLİK: Motor {} dakikadır çalışıyor (limit: {}), kapatılıyor!",
                        runtimeMinutes, maxRuntime);
                controlMotor("off", MotorSource.AUTO);
            }
        });
    }

    // ============================================================
    // MOTOR HEARTBEAT
    // ============================================================

    private void startHeartbeat() {
        stopHeartbeat();
        motorHeartbeat = heartbeatExecutor.scheduleAtFixedRate(
                () -> mqttService.sendCommand("MOTOR_ON"),
                0, 2, TimeUnit.SECONDS);
        log.info("Motor heartbeat başlatıldı (2s aralık)");
    }

    private void stopHeartbeat() {
        ScheduledFuture<?> current = motorHeartbeat;
        if (current != null && !current.isCancelled()) {
            current.cancel(false);
            motorHeartbeat = null;
            log.info("Motor heartbeat durduruldu");
        }
    }

    // ============================================================
    // YARDIMCI METODLAR
    // ============================================================

    private IrrigationSchedule findScheduleOrThrow(Long id) {
        return scheduleRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zamanlayıcı bulunamadı: " + id));
    }

    private ScheduleResponse toScheduleResponse(IrrigationSchedule entity) {
        return ScheduleResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .cronExpression(entity.getCronExpression())
                .durationMinutes(entity.getDurationMinutes())
                .zone(entity.getZone())
                .enabled(entity.getEnabled())
                .humanReadable(cronToHumanReadable(entity.getCronExpression()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Cron ifadesini okunabilir Türkçe metne çevir
     */
    private String cronToHumanReadable(String cron) {
        try {
            String[] parts = cron.split(" ");
            if (parts.length < 6) return cron;

            String minute = parts[1];
            String hour = parts[2];
            String dayOfMonth = parts[3];
            String dayOfWeek = parts[5];

            String time = String.format("%s:%s", padZero(hour), padZero(minute));

            if ("*".equals(dayOfMonth) && "?".equals(dayOfWeek)) {
                return "Her gün " + time;
            } else if ("?".equals(dayOfMonth) && !"?".equals(dayOfWeek)) {
                return dayOfWeekToTurkish(dayOfWeek) + " " + time;
            }
            return time;
        } catch (Exception e) {
            return cron;
        }
    }

    private String padZero(String val) {
        if (val == null || val.isEmpty()) return "00";
        return val.length() == 1 ? "0" + val : val;
    }

    private String dayOfWeekToTurkish(String dow) {
        return switch (dow) {
            case "MON", "2" -> "Pazartesi";
            case "TUE", "3" -> "Salı";
            case "WED", "4" -> "Çarşamba";
            case "THU", "5" -> "Perşembe";
            case "FRI", "6" -> "Cuma";
            case "SAT", "7" -> "Cumartesi";
            case "SUN", "1" -> "Pazar";
            default -> dow + " günleri";
        };
    }
}

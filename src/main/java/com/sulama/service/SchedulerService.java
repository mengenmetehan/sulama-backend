package com.sulama.service;

import com.sulama.model.IrrigationSchedule;
import com.sulama.model.enums.MotorSource;
import com.sulama.repository.ScheduleRepository;
import com.sulama.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final ScheduleRepository scheduleRepo;
    private final IrrigationService irrigationService;
    private final MqttService mqttService;
    private final FcmService fcmService;
    private final UserRepository userRepository;

    /** Son tetiklenme zamanları — tekrar tetiklenmesini önlemek için */
    private final Map<Long, LocalDateTime> lastTriggered = new ConcurrentHashMap<>();

    /** Offline bildirimi gönderildi mi — cihaz tekrar online olana kadar tekrar gönderme */
    private volatile boolean offlineNotificationSent = false;

    /** Zamanlayıcı kapatma görevleri için executor */
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "irrigation-scheduler");
                t.setDaemon(true);
                return t;
            });

    /**
     * Her dakika çalışır — aktif zamanlayıcıları kontrol eder.
     */
    @Scheduled(cron = "0 * * * * *")
    public void checkSchedules() {
        LocalDateTime now = LocalDateTime.now();
        List<IrrigationSchedule> activeSchedules = scheduleRepo.findByEnabledTrue();

        for (IrrigationSchedule schedule : activeSchedules) {
            try {
                if (shouldTrigger(schedule, now)) {
                    triggerSchedule(schedule);
                }
            } catch (Exception e) {
                log.error("Zamanlayıcı kontrol hatası [{}]: {}",
                        schedule.getName(), e.getMessage());
            }
        }
    }

    /**
     * Her dakika ESP32 heartbeat kontrolü — 5 dakika sessizlik varsa bildirim gönder.
     */
    @Scheduled(cron = "0 * * * * *")
    public void checkDeviceHeartbeat() {
        LocalDateTime lastHeartbeat = mqttService.getLastHeartbeatTime();

        // Hiç mesaj gelmemişse (uygulama yeni başladı) bildirim gönderme
        if (lastHeartbeat == null) return;

        boolean isOffline = lastHeartbeat.isBefore(LocalDateTime.now().minusMinutes(2));

        if (isOffline && !offlineNotificationSent) {
            List<String> tokens = userRepository.findAllActiveFcmTokens();
            fcmService.sendDeviceOfflineNotification(tokens);
            offlineNotificationSent = true;
            log.warn("Cihaz çevrimdışı bildirimi gönderildi (son heartbeat: {})", lastHeartbeat);
        } else if (!isOffline && offlineNotificationSent) {
            List<String> tokens = userRepository.findAllActiveFcmTokens();
            fcmService.sendDeviceOnlineNotification(tokens);
            offlineNotificationSent = false;
            log.info("Cihaz tekrar çevrimiçi bildirimi gönderildi");
        }
    }

    /**
     * TEST — isOffline=true senaryosunu zorla tetikler.
     * Heartbeat'e bakmadan, gerçek FCM yoluyla kayıtlı tüm token'lara
     * "Cihaz Çevrimdışı" bildirimi gönderir (checkDeviceHeartbeat ile aynı dal).
     *
     * @return bildirim gönderilen token sayısı
     */
    public int triggerOfflineNotificationForTest() {
        List<String> tokens = userRepository.findAllActiveFcmTokens();
        fcmService.sendDeviceOfflineNotification(tokens);
        offlineNotificationSent = true;
        log.warn("[TEST] Cihaz çevrimdışı bildirimi tetiklendi ({} token)", tokens.size());
        return tokens.size();
    }

    /**
     * Her 5 dakikada motor güvenlik kontrolü.
     */
    @Scheduled(fixedRate = 300_000)
    public void motorSafetyCheck() {
        try {
            irrigationService.checkMotorSafety();
        } catch (Exception e) {
            log.error("Motor güvenlik kontrolü hatası", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ============================================================

    private boolean shouldTrigger(IrrigationSchedule schedule, LocalDateTime now) {
        try {
            CronExpression cron = CronExpression.parse(schedule.getCronExpression());
            LocalDateTime nextExecution = cron.next(now.minusMinutes(1));

            if (nextExecution == null) return false;

            // Bu dakika içinde tetiklenmeli mi?
            boolean isTimeToRun = Math.abs(
                    ChronoUnit.SECONDS.between(nextExecution, now)) < 60;

            if (!isTimeToRun) return false;

            // Son 5 dakikada zaten tetiklendi mi? (çift tetikleme önleme)
            LocalDateTime lastTrigger = lastTriggered.get(schedule.getId());
            return lastTrigger == null ||
                   ChronoUnit.MINUTES.between(lastTrigger, now) >= 5;

        } catch (IllegalArgumentException e) {
            log.error("Geçersiz cron ifadesi [{}]: {}",
                    schedule.getName(), schedule.getCronExpression());
            return false;
        }
    }

    private void triggerSchedule(IrrigationSchedule schedule) {
        log.info("Zamanlayıcı tetiklendi: {} (süre: {} dk, bölge: {})",
                schedule.getName(), schedule.getDurationMinutes(), schedule.getZone());

        lastTriggered.put(schedule.getId(), LocalDateTime.now());

        // Motoru aç
        irrigationService.controlMotor("on", MotorSource.SCHEDULE, schedule.getId());

        // Süre dolunca kapat
        executor.schedule(() -> {
            try {
                log.info("Zamanlayıcı süresi doldu — motor kapatılıyor: {}",
                        schedule.getName());
                irrigationService.controlMotor("off", MotorSource.SCHEDULE, schedule.getId());
            } catch (Exception e) {
                log.error("Zamanlayıcı kapatma hatası [{}]", schedule.getName(), e);
            }
        }, schedule.getDurationMinutes(), TimeUnit.MINUTES);
    }
}

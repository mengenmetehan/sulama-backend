package com.sulama.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.sulama.model.enums.MotorSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class FcmService {

    @Value("${firebase.enabled:true}")
    private boolean enabled;

    public void sendToTokens(List<String> tokens, String title, String body) {
        if (!enabled || FirebaseApp.getApps().isEmpty()) {
            log.debug("FCM devre dışı — bildirim atlanıyor: {}", title);
            return;
        }

        List<String> validTokens = tokens.stream()
                .filter(t -> t != null && !t.isBlank())
                .toList();

        if (validTokens.isEmpty()) {
            log.debug("Kayıtlı FCM token yok — bildirim atlanıyor");
            return;
        }

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(validTokens)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putData("title", title)
                .putData("body", body)
                // Android: yüksek öncelik + heads-up (ekran üstünde beliren) bildirim
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId("default")
                                .setPriority(AndroidNotification.Priority.HIGH)
                                .setSound("default")
                                .build())
                        .build())
                // iOS: ses + kilit ekranında/afişte göster
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setSound("default")
                                .build())
                        .build())
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("FCM gönderildi: {}/{} başarılı", response.getSuccessCount(), validTokens.size());

            if (response.getFailureCount() > 0) {
                for (int i = 0; i < response.getResponses().size(); i++) {
                    SendResponse r = response.getResponses().get(i);
                    if (!r.isSuccessful()) {
                        log.warn("FCM token başarısız [{}]: {}", validTokens.get(i),
                                r.getException().getMessage());
                    }
                }
            }
        } catch (FirebaseMessagingException e) {
            log.error("FCM gönderme hatası: {}", e.getMessage());
        }
    }

    public boolean sendTestNotification(String token) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FCM test: Firebase başlatılmamış");
            return false;
        }
        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle("Test Bildirimi")
                        .setBody("Firebase bağlantısı başarılı!")
                        .build())
                .putData("type", "test")
                .build();
        try {
            String messageId = FirebaseMessaging.getInstance().send(message);
            log.info("FCM test başarılı, messageId: {}", messageId);
            return true;
        } catch (FirebaseMessagingException e) {
            log.error("FCM test başarısız: {}", e.getMessage());
            return false;
        }
    }

    public void sendDeviceOffline2mNotification(List<String> tokens) {
        sendToTokens(tokens, "Cihaz Çevrimdışı", "ESP32 sulama cihazından 2 dakikadır yanıt alınamıyor.");
    }

    public void sendDeviceOffline10mNotification(List<String> tokens) {
        sendToTokens(tokens, "Cihaz Hâlâ Çevrimdışı", "ESP32 sulama cihazına 10 dakikadır ulaşılamıyor.");
    }

    public void sendDeviceOffline30mNotification(List<String> tokens) {
        sendToTokens(tokens, "Cihaz Çevrimdışı (30 dk)", "ESP32 sulama cihazına 30 dakikadır ulaşılamıyor. Kontrol edin.");
    }

    public void sendDeviceOnlineNotification(List<String> tokens) {
        sendToTokens(tokens, "Cihaz Tekrar Çevrimiçi", "ESP32 sulama cihazı yeniden bağlandı.");
    }

    public void sendMotorOnNotification(List<String> tokens, MotorSource source) {
        sendToTokens(tokens, "Motor Açıldı",
                String.format("Sulama motoru açıldı (%s).", sourceLabel(source)));
    }

    public void sendMotorOffNotification(List<String> tokens, MotorSource source, long runtimeMinutes) {
        sendToTokens(tokens, "Motor Kapandı",
                String.format("Sulama motoru kapandı (%s). Çalışma süresi: %d dk.",
                        sourceLabel(source), runtimeMinutes));
    }

    private String sourceLabel(MotorSource source) {
        if (source == null) return "bilinmiyor";
        return switch (source) {
            case MANUAL -> "manuel";
            case SCHEDULE -> "zamanlayıcı";
            case AUTO -> "otomatik";
        };
    }
}

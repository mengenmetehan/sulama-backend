package com.sulama.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class FcmService {

    public void sendToTokens(List<String> tokens, String title, String body) {
        if (FirebaseApp.getApps().isEmpty()) {
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

    public void sendDeviceOfflineNotification(List<String> tokens) {
        sendToTokens(tokens, "Cihaz Çevrimdışı", "ESP32 sulama cihazından 5 dakikadır yanıt alınamıyor.");
    }

    public void sendDeviceOnlineNotification(List<String> tokens) {
        sendToTokens(tokens, "Cihaz Tekrar Çevrimiçi", "ESP32 sulama cihazı yeniden bağlandı.");
    }
}

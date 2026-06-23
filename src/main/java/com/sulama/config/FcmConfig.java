package com.sulama.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Slf4j
@Configuration
public class FcmConfig {

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @PostConstruct
    public void initFirebase() {
        if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
            log.warn("FCM: firebase.service-account-path yapılandırılmamış — push bildirimleri devre dışı");
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try (FileInputStream serviceAccount = new FileInputStream(serviceAccountPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase başlatıldı: {}", serviceAccountPath);
        } catch (IOException e) {
            log.error("Firebase başlatma hatası — bildirimler devre dışı: {}", e.getMessage());
        }
    }
}

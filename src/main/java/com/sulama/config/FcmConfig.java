package com.sulama.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class FcmConfig {

    @Value("${firebase.enabled:true}")
    private boolean enabled;

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${firebase.service-account-json:}")
    private String serviceAccountJson;

    @PostConstruct
    public void initFirebase() {
        if (!enabled) {
            log.info("FCM: firebase.enabled=false — push bildirimleri devre dışı");
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try {
            InputStream credentialsStream = resolveCredentials();
            if (credentialsStream == null) {
                log.warn("FCM: service account yapılandırılmamış — push bildirimleri devre dışı");
                return;
            }
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase başlatıldı");
        } catch (IOException e) {
            log.error("Firebase başlatma hatası — bildirimler devre dışı: {}", e.getMessage());
        }
    }

    private InputStream resolveCredentials() throws IOException {
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            return new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
        }
        if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
            return new FileInputStream(serviceAccountPath);
        }
        return null;
    }
}

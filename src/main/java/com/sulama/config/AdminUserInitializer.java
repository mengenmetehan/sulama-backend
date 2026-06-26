package com.sulama.config;

import com.sulama.model.User;
import com.sulama.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppUsersProperties appUsersProperties;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:sulama2026}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        // Admin kullanıcısı
        upsertUser(adminUsername, adminPassword, "ROLE_ADMIN");

        // Config/env ile tanımlanan diğer kullanıcılar
        appUsersProperties.getUsers().forEach(u ->
                upsertUser(u.getUsername(), u.getPassword(), u.getRole()));
    }

    /**
     * Kullanıcı yoksa oluşturur, varsa şifresi değiştiyse günceller.
     * Mevcut kullanıcının fcm_token gibi diğer alanları korunur.
     */
    private void upsertUser(String username, String rawPassword, String role) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(rawPassword)) {
            log.warn("Kullanıcı seed atlandı — kullanıcı adı veya şifre boş: {}", username);
            return;
        }

        userRepository.findByUsername(username).ifPresentOrElse(
            existing -> {
                if (!passwordEncoder.matches(rawPassword, existing.getPassword())) {
                    existing.setPassword(passwordEncoder.encode(rawPassword));
                    userRepository.save(existing);
                    log.info("Kullanıcı şifresi güncellendi: {}", username);
                }
            },
            () -> {
                User user = User.builder()
                        .username(username)
                        .password(passwordEncoder.encode(rawPassword))
                        .role(role)
                        .enabled(true)
                        .build();
                userRepository.save(user);
                log.info("Kullanıcı oluşturuldu: {} ({})", username, role);
            }
        );
    }
}

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

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:sulama2026}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        userRepository.findByUsername(adminUsername).ifPresentOrElse(
            existing -> {
                if (!passwordEncoder.matches(adminPassword, existing.getPassword())) {
                    existing.setPassword(passwordEncoder.encode(adminPassword));
                    userRepository.save(existing);
                    log.info("Admin şifresi güncellendi: {}", adminUsername);
                }
            },
            () -> {
                User admin = User.builder()
                        .username(adminUsername)
                        .password(passwordEncoder.encode(adminPassword))
                        .role("ROLE_ADMIN")
                        .enabled(true)
                        .build();
                userRepository.save(admin);
                log.info("Admin kullanıcısı oluşturuldu: {}", adminUsername);
            }
        );
    }
}

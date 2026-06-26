package com.sulama.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Uygulamaya giriş yapacak kullanıcıların config/env üzerinden tanımlandığı liste.
 * Açılışta {@link AdminUserInitializer} tarafından DB'ye seed edilir.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppUsersProperties {

    private List<UserCredential> users = new ArrayList<>();

    @Data
    public static class UserCredential {
        private String username;
        private String password;
        private String role = "ROLE_USER";
    }
}

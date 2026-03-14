package com.sulama.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoint'leri herkese açık
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        // H2 console (sadece dev)
                        .requestMatchers("/h2-console/**").permitAll()
                        // Sağlık kontrolü açık
                        .requestMatchers(HttpMethod.GET, "/api/irrigation/health").permitAll()
                        // Okuma endpoint'leri açık
                        .requestMatchers(HttpMethod.GET, "/api/irrigation/**").permitAll()
                        // Motor kontrolü — sadece giriş yapılmış kullanıcılar
                        .requestMatchers(HttpMethod.POST, "/api/irrigation/motor/**").authenticated()
                        // Schedule yönetimi — sadece giriş yapılmış kullanıcılar
                        .requestMatchers(HttpMethod.POST, "/api/irrigation/schedules/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/irrigation/schedules/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/irrigation/schedules/**").authenticated()
                        // Diğer her şey için kimlik doğrulama gerekli
                        .anyRequest().authenticated()
                )
                // H2 console iframe için
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

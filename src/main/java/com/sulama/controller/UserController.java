package com.sulama.controller;

import com.sulama.model.dto.FcmTokenRequest;
import com.sulama.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /**
     * Mobil uygulamadan FCM token kaydı
     * PUT /api/users/fcm-token
     */
    @PutMapping("/fcm-token")
    public ResponseEntity<Void> registerFcmToken(
            @Valid @RequestBody FcmTokenRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        userRepository.findByUsername(userDetails.getUsername()).ifPresent(user -> {
            user.setFcmToken(request.token());
            userRepository.save(user);
        });
        return ResponseEntity.ok().build();
    }

    /**
     * Uygulama çıkışında veya bildirim izni reddedilince token sil
     * DELETE /api/users/fcm-token
     */
    @DeleteMapping("/fcm-token")
    public ResponseEntity<Void> removeFcmToken(@AuthenticationPrincipal UserDetails userDetails) {
        userRepository.findByUsername(userDetails.getUsername()).ifPresent(user -> {
            user.setFcmToken(null);
            userRepository.save(user);
        });
        return ResponseEntity.ok().build();
    }
}

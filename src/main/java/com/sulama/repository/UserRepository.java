package com.sulama.repository;

import com.sulama.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    @Query("SELECT u.fcmToken FROM User u WHERE u.fcmToken IS NOT NULL AND u.enabled = true")
    List<String> findAllActiveFcmTokens();
}

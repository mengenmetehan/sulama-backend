package com.sulama.repository;

import com.sulama.model.MotorLog;
import com.sulama.model.enums.MotorAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MotorLogRepository extends JpaRepository<MotorLog, Long> {

    Optional<MotorLog> findTopByOrderByCreatedAtDesc();

    @Query("SELECT m FROM MotorLog m WHERE m.createdAt >= :since ORDER BY m.createdAt DESC")
    List<MotorLog> findRecentLogs(@Param("since") LocalDateTime since);

    @Query("SELECT m FROM MotorLog m WHERE m.action = :action AND m.stoppedAt IS NULL " +
           "ORDER BY m.startedAt DESC")
    Optional<MotorLog> findActiveSession(@Param("action") MotorAction action);

    default Optional<MotorLog> findActiveMotorSession() {
        return findActiveSession(MotorAction.ON);
    }
}

package com.sulama.model;

import com.sulama.model.enums.MotorAction;
import com.sulama.model.enums.MotorSource;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "motor_log",
       indexes = @Index(name = "idx_motor_log_created", columnList = "created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MotorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MotorAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MotorSource source;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "water_used_liters", precision = 10, scale = 2)
    private BigDecimal waterUsedLiters;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "stopped_at")
    private LocalDateTime stoppedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    /**
     * Motor çalışma süresini dakika olarak hesapla
     */
    public long getRuntimeMinutes() {
        if (startedAt == null) return 0;
        LocalDateTime end = stoppedAt != null ? stoppedAt : LocalDateTime.now();
        return java.time.Duration.between(startedAt, end).toMinutes();
    }
}

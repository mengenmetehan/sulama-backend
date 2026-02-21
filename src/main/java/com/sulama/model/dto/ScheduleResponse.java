package com.sulama.model.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponse {
    private Long id;
    private String name;
    private String cronExpression;
    private Integer durationMinutes;
    private String zone;
    private boolean enabled;
    private String humanReadable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

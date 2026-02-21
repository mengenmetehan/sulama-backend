package com.sulama.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleRequest {

    @NotBlank(message = "Zamanlayıcı adı gerekli")
    private String name;

    @NotBlank(message = "Cron ifadesi gerekli")
    private String cronExpression;

    @Positive(message = "Süre pozitif olmalı")
    private Integer durationMinutes;

    private String zone;
    private Boolean enabled;
}

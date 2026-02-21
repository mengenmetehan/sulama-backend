package com.sulama.model.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotorCommandResponse {
    private String status;
    private String action;
    private LocalDateTime timestamp;
}

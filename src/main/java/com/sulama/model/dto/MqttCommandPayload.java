package com.sulama.model.dto;

import lombok.*;

/**
 * Backend'den ESP32'ye gönderilen komut payload'ı.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MqttCommandPayload {
    private String command;  // MOTOR_ON, MOTOR_OFF, STATUS
}

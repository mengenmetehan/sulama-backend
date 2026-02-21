package com.sulama.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "irrigation")
public class IrrigationProperties {

    private SoilMoisture soilMoisture = new SoilMoisture();
    private Motor motor = new Motor();

    @Data
    public static class SoilMoisture {
        private int lowThreshold = 30;
        private int highThreshold = 70;
    }

    @Data
    public static class Motor {
        private int maxRuntimeMinutes = 180;
        private int pulseConfirmationTimeoutSeconds = 10;
    }
}

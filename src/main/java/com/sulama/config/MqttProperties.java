package com.sulama.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    private Broker broker = new Broker();
    private Topics topics = new Topics();

    @Data
    public static class Broker {
        private String url = "tcp://localhost:1883";
        private String clientId = "sulama-backend";
        private String username = "sulama";
        private String password = "sulama_mqtt_2024";
        private int connectionTimeout = 30;
        private int keepAliveInterval = 60;
        private boolean automaticReconnect = true;
        private boolean mockEnabled = false;
    }

    @Data
    public static class Topics {
        private String command = "sulama/command";
        private String status = "sulama/status";
    }
}

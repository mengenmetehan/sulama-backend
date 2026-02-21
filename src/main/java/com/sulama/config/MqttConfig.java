package com.sulama.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttConfig {

    private final MqttProperties mqttProperties;

    @Bean
    public MqttClient mqttClient() {
        MqttProperties.Broker broker = mqttProperties.getBroker();

        if (broker.isMockEnabled()) {
            log.warn("MQTT Mock modu aktif — gerçek broker'a bağlanılmayacak");
            return null;
        }

        try {
            MqttClient client = new MqttClient(
                    broker.getUrl(),
                    broker.getClientId(),
                    new MemoryPersistence()
            );

            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(broker.getUsername());
            options.setPassword(broker.getPassword().toCharArray());
            options.setAutomaticReconnect(broker.isAutomaticReconnect());
            options.setCleanSession(false);
            options.setKeepAliveInterval(broker.getKeepAliveInterval());
            options.setConnectionTimeout(broker.getConnectionTimeout());

            client.connect(options);
            log.info("MQTT Broker'a bağlandı: {}", broker.getUrl());

            return client;

        } catch (MqttException e) {
            log.error("MQTT Broker'a bağlanılamadı: {} — Mock moda geçiliyor", e.getMessage());
            return null;
        }
    }
}

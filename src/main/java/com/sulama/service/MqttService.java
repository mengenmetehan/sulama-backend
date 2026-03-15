package com.sulama.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sulama.config.MqttProperties;
import com.sulama.model.DeviceStatus;
import com.sulama.model.SensorReading;
import com.sulama.model.dto.DeviceStatusResponse;
import com.sulama.model.dto.MqttCommandPayload;
import com.sulama.model.dto.MqttStatusPayload;
import com.sulama.repository.DeviceStatusRepository;
import com.sulama.repository.SensorReadingRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttService {

    @Nullable
    private final MqttClient mqttClient;
    private final MqttProperties mqttProperties;
    private final ObjectMapper objectMapper;
    private final DeviceStatusRepository deviceStatusRepo;
    private final SensorReadingRepository sensorReadingRepo;

    private final AtomicReference<DeviceStatusResponse> lastStatus =
            new AtomicReference<>(DeviceStatusResponse.builder()
                    .motorRunning(false)
                    .deviceOnline(false)
                    .build());

    private volatile LocalDateTime lastHeartbeat;

    @PostConstruct
    public void init() {
        if (mqttClient == null) {
            log.warn("MQTT Mock modu — subscribe yapılmayacak");
            return;
        }

        try {
            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("MQTT {}: {}", reconnect ? "yeniden bağlandı" : "bağlandı", serverURI);
                    subscribeToTopics();
                }

                @Override
                public void connectionLost(Throwable cause) {
                    if (cause instanceof MqttException mqttEx) {
                        log.warn("MQTT bağlantısı koptu (kod: {}): {}", mqttEx.getReasonCode(), cause.getMessage());
                    } else {
                        log.warn("MQTT bağlantısı koptu: {}", cause.getMessage());
                    }
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleIncomingMessage(topic, new String(message.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    try {
                        log.info("MQTT broker mesajı onayladı (messageId: {})", token.getMessageId());
                    } catch (Exception e) {
                        log.warn("MQTT delivery token okunamadı: {}", e.getMessage());
                    }
                }
            });

            subscribeToTopics();

        } catch (Exception e) {
            log.error("MQTT callback ayarlama hatası", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                log.info("MQTT bağlantısı kapatıldı");
            } catch (MqttException e) {
                log.error("MQTT disconnect hatası", e);
            }
        }
    }

    private void subscribeToTopics() {
        if (mqttClient == null) return;
        try {
            String statusTopic = mqttProperties.getTopics().getStatus();
            mqttClient.subscribe(statusTopic, 1);
            log.info("MQTT subscribe: {}", statusTopic);
        } catch (MqttException e) {
            log.error("MQTT subscribe hatası", e);
        }
    }

    /**
     * ESP32'den gelen status mesajlarını işle
     */
    private void handleIncomingMessage(String topic, String payload) {
        try {
            log.debug("MQTT geldi [{}]: {}", topic, payload);

            MqttStatusPayload mqttPayload = objectMapper.readValue(payload, MqttStatusPayload.class);
            lastHeartbeat = LocalDateTime.now();

            // Cache güncelle
            DeviceStatusResponse statusResponse = DeviceStatusResponse.builder()
                    .motorRunning(mqttPayload.isMotor())
                    .soilMoisture(mqttPayload.getSoilMoisture())
                    .waterUsedLiters(mqttPayload.getWaterUsedLiters())
                    .temperature(mqttPayload.getTemperature())
                    .wifiRssi(mqttPayload.getWifiRssi())
                    .uptimeSeconds(mqttPayload.getUptimeSeconds())
                    .deviceOnline(true)
                    .lastSeen(lastHeartbeat)
                    .build();
            lastStatus.set(statusResponse);

            // Veritabanına kaydet
            persistStatus(mqttPayload);

        } catch (Exception e) {
            log.error("MQTT mesaj işleme hatası: {}", payload, e);
        }
    }

    private void persistStatus(MqttStatusPayload payload) {
        DeviceStatus entity = DeviceStatus.builder()
                .motorRunning(payload.isMotor())
                .soilMoisture(payload.getSoilMoisture())
                .waterUsedLiters(payload.getWaterUsedLiters())
                .temperature(payload.getTemperature())
                .wifiRssi(payload.getWifiRssi())
                .uptimeSeconds(payload.getUptimeSeconds())
                .reportedAt(LocalDateTime.now())
                .build();
        deviceStatusRepo.save(entity);

        SensorReading reading = SensorReading.builder()
                .soilMoisture(payload.getSoilMoisture())
                .temperature(payload.getTemperature())
                .recordedAt(LocalDateTime.now())
                .build();
        sensorReadingRepo.save(reading);
    }

    /**
     * ESP32'ye komut gönder
     */
    public void sendCommand(String command) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            log.warn("MQTT bağlı değil — komut simüle ediliyor: {}", command);
            simulateCommand(command);
            return;
        }

        try {
            MqttCommandPayload payload = new MqttCommandPayload(command);
            String json = objectMapper.writeValueAsString(payload);

            MqttMessage message = new MqttMessage(json.getBytes());
            message.setQos(1);
            message.setRetained(false);

            mqttClient.publish(mqttProperties.getTopics().getCommand(), message);
            log.info("MQTT komut gönderildi: {}", command);

        } catch (Exception e) {
            log.error("MQTT komut gönderme hatası: {}", command, e);
            throw new RuntimeException("Motor komutu gönderilemedi: " + e.getMessage(), e);
        }
    }

    /**
     * Mock mode — MQTT broker yokken komutları simüle et
     */
    private void simulateCommand(String command) {
        DeviceStatusResponse current = lastStatus.get();
        boolean motorOn = "MOTOR_ON".equals(command);

        DeviceStatusResponse updated = DeviceStatusResponse.builder()
                .motorRunning(motorOn)
                .soilMoisture(current.getSoilMoisture() != null ? current.getSoilMoisture() : 45)
                .waterUsedLiters(current.getWaterUsedLiters())
                .temperature(current.getTemperature())
                .wifiRssi(-55)
                .uptimeSeconds(current.getUptimeSeconds())
                .deviceOnline(true)
                .lastSeen(LocalDateTime.now())
                .build();

        lastStatus.set(updated);
        lastHeartbeat = LocalDateTime.now();
        log.info("MOCK — Motor {}", motorOn ? "AÇILDI" : "KAPATILDI");
    }

    /**
     * Son cihaz durumunu getir
     */
    public DeviceStatusResponse getLastStatus() {
        DeviceStatusResponse status = lastStatus.get();

        // 2 dakikadan eski ise çevrimdışı say
        if (lastHeartbeat != null) {
            boolean online = lastHeartbeat.isAfter(LocalDateTime.now().minusMinutes(2));
            status.setDeviceOnline(online);
            status.setLastSeen(lastHeartbeat);
        }

        return status;
    }

    /**
     * MQTT bağlantı durumu
     */
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }
}

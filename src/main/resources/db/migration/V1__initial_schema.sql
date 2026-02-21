-- ============================================================
-- Sulama Sistemi - Veritabanı Şeması V1
-- H2 ve PostgreSQL uyumlu
-- ============================================================

-- Cihaz anlık durum geçmişi
CREATE TABLE IF NOT EXISTS device_status (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id           VARCHAR(50)    NOT NULL DEFAULT 'ESP32_MAIN',
    motor_running       BOOLEAN        NOT NULL DEFAULT FALSE,
    soil_moisture       INT,
    water_used_liters   DECIMAL(10,2),
    temperature         DECIMAL(5,2),
    wifi_rssi           INT,
    uptime_seconds      BIGINT,
    reported_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_device_status_device_reported
    ON device_status(device_id, reported_at);

-- Sulama zamanlayıcıları
CREATE TABLE IF NOT EXISTS irrigation_schedule (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(100)   NOT NULL,
    cron_expression     VARCHAR(50)    NOT NULL,
    duration_minutes    INT            NOT NULL,
    zone                VARCHAR(20)    NOT NULL DEFAULT 'ALL',
    enabled             BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Motor çalışma logları
CREATE TABLE IF NOT EXISTS motor_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    action              VARCHAR(10)    NOT NULL,
    source              VARCHAR(20)    NOT NULL,
    schedule_id         BIGINT,
    water_used_liters   DECIMAL(10,2),
    started_at          TIMESTAMP,
    stopped_at          TIMESTAMP,
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_motor_log_schedule
        FOREIGN KEY (schedule_id) REFERENCES irrigation_schedule(id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_motor_log_created
    ON motor_log(created_at);

-- Sensör veri geçmişi
CREATE TABLE IF NOT EXISTS sensor_reading (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id           VARCHAR(50)    NOT NULL DEFAULT 'ESP32_MAIN',
    soil_moisture       INT,
    temperature         DECIMAL(5,2),
    water_flow_lpm      DECIMAL(8,2),
    recorded_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sensor_reading_recorded
    ON sensor_reading(recorded_at);

CREATE INDEX IF NOT EXISTS idx_sensor_reading_device_recorded
    ON sensor_reading(device_id, recorded_at);

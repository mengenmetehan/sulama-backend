-- ============================================================
-- V2: Geliştirme için örnek veri
-- ============================================================

-- Örnek zamanlayıcılar
INSERT INTO irrigation_schedule (name, cron_expression, duration_minutes, zone, enabled) VALUES
    ('Sabah Sulama', '0 0 6 * * ?', 120, 'ALL', true),
    ('Akşam Sulama', '0 0 18 * * ?', 90, 'DAMLA', true),
    ('Öğlen Kontrol', '0 0 12 * * ?', 30, 'KARIK', false);

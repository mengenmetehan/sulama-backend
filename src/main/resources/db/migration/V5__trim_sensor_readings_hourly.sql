-- Sensör geçmişi artık günlük gösteriliyor; dakikalık kayıtlar gereksiz.
-- Her saat için ilk kaydı tut, kalanını sil.
DELETE FROM sensor_reading
WHERE id NOT IN (
    SELECT MIN(id)
    FROM sensor_reading
    GROUP BY device_id, DATE_TRUNC('hour', recorded_at)
);

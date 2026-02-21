package com.sulama.repository;

import com.sulama.model.SensorReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    @Query("SELECT s FROM SensorReading s WHERE s.deviceId = :deviceId " +
           "AND s.recordedAt >= :since ORDER BY s.recordedAt ASC")
    List<SensorReading> findRecent(
            @Param("deviceId") String deviceId,
            @Param("since") LocalDateTime since
    );
}

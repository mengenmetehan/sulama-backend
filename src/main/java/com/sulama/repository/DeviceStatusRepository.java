package com.sulama.repository;

import com.sulama.model.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceStatusRepository extends JpaRepository<DeviceStatus, Long> {

    Optional<DeviceStatus> findTopByDeviceIdOrderByReportedAtDesc(String deviceId);

    @Query("SELECT d FROM DeviceStatus d WHERE d.deviceId = :deviceId " +
           "AND d.reportedAt BETWEEN :from AND :to ORDER BY d.reportedAt ASC")
    List<DeviceStatus> findByDeviceIdAndTimeRange(
            @Param("deviceId") String deviceId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}

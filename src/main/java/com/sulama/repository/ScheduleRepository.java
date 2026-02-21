package com.sulama.repository;

import com.sulama.model.IrrigationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<IrrigationSchedule, Long> {

    List<IrrigationSchedule> findByEnabledTrue();

    List<IrrigationSchedule> findAllByOrderByCreatedAtDesc();
}

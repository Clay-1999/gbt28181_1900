package com.example.gbt28181.domain.repository;

import com.example.gbt28181.domain.entity.AlarmEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlarmEventRepository extends JpaRepository<AlarmEvent, Long> {

    List<AlarmEvent> findByDeviceIdOrderByReceivedAtDesc(String deviceId);

    List<AlarmEvent> findAllByOrderByReceivedAtDesc();

    Page<AlarmEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);

    Page<AlarmEvent> findByDeviceIdOrderByReceivedAtDesc(String deviceId, Pageable pageable);
}

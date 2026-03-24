package com.example.gbt28181.domain.repository;

import com.example.gbt28181.domain.entity.RemoteDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RemoteDeviceRepository extends JpaRepository<RemoteDevice, Long> {
    List<RemoteDevice> findByInterconnectConfigId(Long interconnectConfigId);
    Optional<RemoteDevice> findByDeviceId(String deviceId);
}

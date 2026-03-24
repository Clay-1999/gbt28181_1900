package com.example.gbt28181.domain.repository;

import com.example.gbt28181.domain.entity.Ivs1900CameraMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface Ivs1900CameraMappingRepository extends JpaRepository<Ivs1900CameraMapping, Long> {
    Optional<Ivs1900CameraMapping> findByIvsCameraId(String ivsCameraId);
    Optional<Ivs1900CameraMapping> findByGbDeviceId(String gbDeviceId);
}

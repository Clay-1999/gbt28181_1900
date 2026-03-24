package com.example.gbt28181.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ivs1900_camera_mapping")
@Getter
@Setter
public class Ivs1900CameraMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ivs_camera_id", nullable = false, unique = true)
    private String ivsCameraId;

    @Column(name = "gb_device_id", nullable = false, unique = true, length = 20)
    private String gbDeviceId;

    @Column(name = "name")
    private String name;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "domain_code")
    private String domainCode;

    @Column(name = "ptz_type")
    private String ptzType;
}

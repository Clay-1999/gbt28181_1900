package com.example.gbt28181.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "remote_device")
@Getter
@Setter
public class RemoteDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;

    @Column(name = "name")
    private String name;

    @Column(name = "status")
    private String status;

    @Column(name = "interconnect_config_id", nullable = false)
    private Long interconnectConfigId;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "ptz_type")
    private String ptzType;
}

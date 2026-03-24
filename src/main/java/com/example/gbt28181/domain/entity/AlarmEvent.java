package com.example.gbt28181.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "alarm_event")
@Getter
@Setter
public class AlarmEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "alarm_priority")
    private String alarmPriority;

    @Column(name = "alarm_method")
    private String alarmMethod;

    @Column(name = "alarm_type")
    private String alarmType;

    @Column(name = "alarm_description", length = 1024)
    private String alarmDescription;

    @Column(name = "alarm_time")
    private String alarmTime;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "source_ip")
    private String sourceIp;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}

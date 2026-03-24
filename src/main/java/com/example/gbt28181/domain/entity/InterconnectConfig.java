package com.example.gbt28181.domain.entity;

import com.example.gbt28181.domain.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "interconnect_config")
@Getter
@Setter
public class InterconnectConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "remote_sip_id", nullable = false)
    private String remoteSipId;

    @Column(name = "remote_ip", nullable = false)
    private String remoteIp;

    @Column(name = "remote_port", nullable = false)
    private Integer remotePort;

    @Column(name = "remote_domain", nullable = false)
    private String remoteDomain;

    @Column(name = "password")
    @Convert(converter = EncryptedStringConverter.class)
    private String password;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "up_link_enabled", nullable = false, columnDefinition = "boolean default false")
    private Boolean upLinkEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "up_link_status", length = 20)
    private LinkStatus upLinkStatus = LinkStatus.OFFLINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "down_link_status", length = 20)
    private LinkStatus downLinkStatus = LinkStatus.OFFLINE;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

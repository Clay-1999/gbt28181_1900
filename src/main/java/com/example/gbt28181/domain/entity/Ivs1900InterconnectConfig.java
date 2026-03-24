package com.example.gbt28181.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ivs1900_interconnect_config")
@Getter
@Setter
public class Ivs1900InterconnectConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** IVS1900 的 GB/T 28181 SIP 设备 ID（20 位） */
    @Column(name = "sip_id", nullable = false)
    private String sipId;

    /** IVS1900 SIP 监听 IP */
    @Column(name = "ip", nullable = false)
    private String ip;

    /** IVS1900 SIP 端口 */
    @Column(name = "port", nullable = false)
    private Integer port;

    /** IVS1900 SIP 域 */
    @Column(name = "domain", nullable = false)
    private String domain;

    /** Digest 认证密码（明文存储） */
    @Column(name = "password")
    private String password;

    /** 上联注册状态（OFFLINE/ONLINE），由 SipRegistrationServer 维护 */
    @Enumerated(EnumType.STRING)
    @Column(name = "up_link_status", length = 20)
    private LinkStatus upLinkStatus = LinkStatus.OFFLINE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

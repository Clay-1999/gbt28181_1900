package com.example.gbt28181.domain.entity;

import com.example.gbt28181.domain.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "local_sip_config")
@Getter
@Setter
public class LocalSipConfig {

    @Id
    private Long id = 1L;

    @Column(name = "device_id", length = 20)
    private String deviceId;

    @Column(name = "domain")
    private String domain;

    @Column(name = "sip_ip")
    private String sipIp;

    @Column(name = "sip_port")
    private Integer sipPort = 5060;

    @Column(name = "transport", length = 10)
    private String transport = "UDP";

    @Column(name = "password")
    @Convert(converter = EncryptedStringConverter.class)
    private String password;

    @Column(name = "expires")
    private Integer expires = 3600;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private SipStackStatus status = SipStackStatus.ERROR;

    @Column(name = "error_msg", length = 500)
    private String errorMsg;
}

package com.example.gbt28181.api.dto;

import com.example.gbt28181.domain.entity.LinkStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
public class InterconnectConfigResponse {

    private Long id;
    private String name;
    private String remoteSipId;
    private String remoteIp;
    private Integer remotePort;
    private String remoteDomain;
    private String password = "***";
    private Boolean enabled;
    private Boolean upLinkEnabled;
    private LinkStatus upLinkStatus;
    private LinkStatus downLinkStatus;
    private Instant lastHeartbeatAt;
    private LocalDateTime createdAt;
}

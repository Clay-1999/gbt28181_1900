package com.example.gbt28181.api.dto;

import com.example.gbt28181.domain.entity.SipStackStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LocalSipConfigResponse {

    private String deviceId;
    private String domain;
    private String sipIp;
    private Integer sipPort;
    private String transport;
    private String password = "***";
    private Integer expires;
    private SipStackStatus status;
    private String errorMsg;
}

package com.example.gbt28181.api.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class RemoteDeviceResponse {
    private String deviceId;
    private String name;
    private String status;
    private String ptzType;
    private String interconnectName;
    private LocalDateTime syncedAt;
}

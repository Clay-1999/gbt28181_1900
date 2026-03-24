package com.example.gbt28181.api.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DeviceResponse {
    private Long id;
    private String gbDeviceId;
    private String name;
    private String status;
    private String ptzType;
    private LocalDateTime syncedAt;
}

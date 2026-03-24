package com.example.gbt28181.api.dto;

import lombok.Data;

@Data
public class UpgradeRequest {
    private String firmwareId;
    private String firmwareAddr;
}

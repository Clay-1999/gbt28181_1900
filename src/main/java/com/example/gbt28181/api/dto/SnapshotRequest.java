package com.example.gbt28181.api.dto;

import lombok.Data;

@Data
public class SnapshotRequest {
    private int snapNum;
    private int interval;
    private String uploadAddr;
    private String resolution;
}

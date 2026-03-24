package com.example.gbt28181.api.dto;

import lombok.Data;

@Data
public class RecordQueryRequest {
    private String startTime;
    private String endTime;
    private String type = "all";
}

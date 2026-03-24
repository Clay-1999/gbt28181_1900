package com.example.gbt28181.api.dto;

import lombok.Data;

@Data
public class PlaybackControlRequest {
    private String action;
    private Double scale;
    private String seekTime;
}

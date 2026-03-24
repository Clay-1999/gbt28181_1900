package com.example.gbt28181.api.dto;

import lombok.Data;

@Data
public class PtzControlRequest {
    /** 动作：up/down/left/right/left-up/right-up/left-down/right-down/stop/
     *        zoom_in/zoom_out/focus_in/focus_out/iris_in/iris_out */
    private String action;
    /** 速度 0~255，缺省 128 */
    private Integer speed = 128;
    /** 预置位编号（预置位操作时使用） */
    private Integer presetIndex;
    /** 预置位名称（设置预置位时使用） */
    private String presetName;
    /** 巡航轨迹名称（巡航操作时使用） */
    private String trackName;
}

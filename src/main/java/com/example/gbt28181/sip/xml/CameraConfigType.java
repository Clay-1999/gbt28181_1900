package com.example.gbt28181.sip.xml;

/**
 * GB/T 28181 相机配置类型枚举。
 * {@link #name()} 即 XML {@code <ConfigType>} 字段值。
 * {@link #urlSegment()} 对应 REST API URL 路径段（与前端 tab name 对应）。
 */
public enum CameraConfigType {

    VideoParamAttribute("video-param"),
    OSDConfig("osd"),
    PictureMask("picture-mask"),
    FrameMirror("frame-mirror"),
    VideoRecordPlan("video-record-plan"),
    VideoAlarmRecord("video-alarm-record"),
    AlarmReport("alarm-report"),
    SnapShot("snap-shot");

    private final String urlSegment;

    CameraConfigType(String urlSegment) {
        this.urlSegment = urlSegment;
    }

    public String urlSegment() {
        return urlSegment;
    }

    /** 从 URL 路径段解析枚举值，找不到抛出 IllegalArgumentException。 */
    public static CameraConfigType fromUrlSegment(String segment) {
        for (CameraConfigType t : values()) {
            if (t.urlSegment.equals(segment)) return t;
        }
        throw new IllegalArgumentException("Unknown config type segment: " + segment);
    }
}

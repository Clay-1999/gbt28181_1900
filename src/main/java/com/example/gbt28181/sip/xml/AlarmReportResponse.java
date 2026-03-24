package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@XmlRootElement(name = "Response")
@XmlAccessorType(XmlAccessType.FIELD)
public class AlarmReportResponse {

    @XmlElement(name = "CmdType")
    private String cmdType;

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "Result")
    private String result;

    @XmlElement(name = "AlarmReport")
    private AlarmReport alarmReport;

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class AlarmReport {
        /** 移动侦测事件上报开关：0-关闭，1-打开 */
        @XmlElement(name = "MotionDetection")
        private int motionDetection;

        /** 区域入侵事件上报开关：0-关闭，1-打开 */
        @XmlElement(name = "FieldDetection")
        private int fieldDetection;
    }
}

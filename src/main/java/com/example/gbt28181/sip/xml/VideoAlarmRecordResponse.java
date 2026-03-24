package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@XmlRootElement(name = "Response")
@XmlAccessorType(XmlAccessType.FIELD)
public class VideoAlarmRecordResponse {

    @XmlElement(name = "CmdType")
    private String cmdType;

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "Result")
    private String result;

    @XmlElement(name = "VideoAlarmRecord")
    private VideoAlarmRecord videoAlarmRecord;

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class VideoAlarmRecord {
        /** 是否启用报警录像：0-否，1-是 */
        @XmlElement(name = "RecordEnable")
        private int recordEnable;

        /** 录像延时时间（报警后），单位秒（可选） */
        @XmlElement(name = "RecordTime")
        private Integer recordTime;

        /** 预录时间（报警前），单位秒（可选） */
        @XmlElement(name = "PreRecordTime")
        private Integer preRecordTime;

        /** 码流编号：0-主码流，1-子码流1，以此类推 */
        @XmlElement(name = "StreamNumber")
        private int streamNumber;
    }
}

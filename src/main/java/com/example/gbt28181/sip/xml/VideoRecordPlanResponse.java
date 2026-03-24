package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@XmlRootElement(name = "Response")
@XmlAccessorType(XmlAccessType.FIELD)
public class VideoRecordPlanResponse {

    @XmlElement(name = "CmdType")
    private String cmdType;

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "Result")
    private String result;

    @XmlElement(name = "VideoRecordPlan")
    private VideoRecordPlan videoRecordPlan;

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class VideoRecordPlan {
        /** 是否启用时间计划录像：0-否，1-是 */
        @XmlElement(name = "RecordEnable")
        private int recordEnable;

        @XmlElement(name = "RecordScheduleSumNum")
        private int recordScheduleSumNum;

        @XmlElement(name = "RecordSchedule")
        private List<RecordSchedule> recordSchedules;

        /** 码流编号：0-主码流，1-子码流1，以此类推 */
        @XmlElement(name = "StreamNumber")
        private int streamNumber;
    }

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class RecordSchedule {
        /** 周几，取值1~7，表示周一到周日 */
        @XmlElement(name = "WeekDayNum")
        private int weekDayNum;

        @XmlElement(name = "TimeSegmentSumNum")
        private int timeSegmentSumNum;

        @XmlElement(name = "TimeSegment")
        private List<TimeSegment> timeSegments;
    }

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TimeSegment {
        @XmlElement(name = "StartHour") private int startHour;
        @XmlElement(name = "StartMin")  private int startMin;
        @XmlElement(name = "StartSec")  private int startSec;
        @XmlElement(name = "StopHour")  private int stopHour;
        @XmlElement(name = "StopMin")   private int stopMin;
        @XmlElement(name = "StopSec")   private int stopSec;
    }
}

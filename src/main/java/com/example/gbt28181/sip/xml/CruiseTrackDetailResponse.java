package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 巡航轨迹详情查询应答（A.2.6.14）。
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Response")
@XmlAccessorType(XmlAccessType.FIELD)
public class CruiseTrackDetailResponse {

    @XmlElement(name = "CmdType")
    private String cmdType;

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "Number")
    private int number;

    @XmlElement(name = "Name")
    private String name;

    @XmlElement(name = "SumNum")
    private int sumNum;

    @XmlElement(name = "CruisePointList")
    private CruisePointList cruisePointList;

    public List<CruisePoint> getPoints() {
        return cruisePointList != null && cruisePointList.getPoints() != null
                ? cruisePointList.getPoints() : List.of();
    }

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class CruisePointList {
        @XmlAttribute(name = "Num")
        private int num;

        @XmlElement(name = "CruisePoint")
        private List<CruisePoint> points;
    }

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class CruisePoint {
        /** 预置位编号 */
        @XmlElement(name = "PresetIndex")
        private int presetIndex;
        /** 停留时间（秒） */
        @XmlElement(name = "StayTime")
        private int stayTime;
        /** 云台速度 1~15 */
        @XmlElement(name = "Speed")
        private int speed;
    }
}

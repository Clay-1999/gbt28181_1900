package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 巡航轨迹列表查询应答（A.2.6.13）。
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Response")
@XmlAccessorType(XmlAccessType.FIELD)
public class CruiseTrackListResponse {

    @XmlElement(name = "CmdType")
    private String cmdType;

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "SumNum")
    private int sumNum;

    @XmlElement(name = "CruiseTrackList")
    private CruiseTrackList cruiseTrackList;

    public List<CruiseTrackItem> getTracks() {
        return cruiseTrackList != null && cruiseTrackList.getTracks() != null
                ? cruiseTrackList.getTracks() : List.of();
    }

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class CruiseTrackList {
        @XmlAttribute(name = "Num")
        private int num;

        @XmlElement(name = "CruiseTrack")
        private List<CruiseTrackItem> tracks;
    }

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class CruiseTrackItem {
        @XmlElement(name = "Number")
        private int number;
        @XmlElement(name = "Name")
        private String name;
    }
}

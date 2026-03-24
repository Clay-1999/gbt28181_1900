package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件目录检索请求（GB/T 28181 A.2.4.5）。
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Query")
@XmlAccessorType(XmlAccessType.FIELD)
public class RecordInfoQueryXml {

    @XmlElement(name = "CmdType")
    private String cmdType = "RecordInfo";

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "StartTime")
    private String startTime;

    @XmlElement(name = "EndTime")
    private String endTime;

    @XmlElement(name = "Type")
    private String type;

    public RecordInfoQueryXml(int sn, String deviceId, String startTime, String endTime, String type) {
        this.sn = sn;
        this.deviceId = deviceId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.type = type;
    }
}

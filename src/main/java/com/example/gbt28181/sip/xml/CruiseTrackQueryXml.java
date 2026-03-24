package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@XmlRootElement(name = "Query")
@XmlAccessorType(XmlAccessType.FIELD)
public class CruiseTrackQueryXml {

    @XmlElement(name = "CmdType")
    private String cmdType = "CruiseTrackQuery";

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    /** 轨迹编号：0-第一条，1-第二条，以此类推。 */
    @XmlElement(name = "Number")
    private int number;

    public CruiseTrackQueryXml(int sn, String deviceId, int number) {
        this.sn = sn;
        this.deviceId = deviceId;
        this.number = number;
    }
}

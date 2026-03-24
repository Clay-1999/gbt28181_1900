package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@XmlRootElement(name = "Query")
@XmlAccessorType(XmlAccessType.FIELD)
public class PresetQueryXml {

    @XmlElement(name = "CmdType")
    private String cmdType = "PresetQuery";

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    public PresetQueryXml(int sn, String deviceId) {
        this.sn = sn;
        this.deviceId = deviceId;
    }
}

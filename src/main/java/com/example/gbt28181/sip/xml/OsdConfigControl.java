package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@XmlRootElement(name = "Control")
@XmlAccessorType(XmlAccessType.FIELD)
public class OsdConfigControl {

    @XmlElement(name = "CmdType")
    private String cmdType = "DeviceConfig";

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "OSDConfig")
    private OsdConfig osdConfig;

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class OsdConfig {
        @XmlElement(name = "Length")
        private Integer length;

        @XmlElement(name = "Width")
        private Integer width;

        @XmlElement(name = "TimeX")
        private Integer timeX;

        @XmlElement(name = "TimeY")
        private Integer timeY;

        @XmlElement(name = "TimeEnable")
        private int timeEnable;

        @XmlElement(name = "TimeType")
        private Integer timeType;

        @XmlElement(name = "SumNum")
        private Integer sumNum;

        @XmlElement(name = "TextEnable")
        private int textEnable;

        @XmlElement(name = "Item")
        private List<OsdItem> items;
    }

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class OsdItem {
        @XmlElement(name = "Text")
        private String text;

        @XmlElement(name = "X")
        private int x;

        @XmlElement(name = "Y")
        private int y;
    }
}

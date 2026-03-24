package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GB/T 28181 Catalog Notify，根元素 &lt;Notify&gt;。
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Notify")
@XmlAccessorType(XmlAccessType.FIELD)
public class CatalogNotifyXml {

    @XmlElement(name = "CmdType")
    private String cmdType;

    @XmlElement(name = "SN")
    private String sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "SumNum")
    private int sumNum;

    @XmlElement(name = "DeviceList")
    private DeviceList deviceList;

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DeviceList {
        @XmlAttribute(name = "Num")
        private int num;

        @XmlElement(name = "Item")
        private List<CatalogItem> items;
    }
}

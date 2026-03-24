package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GB/T 28181 Catalog Response，根元素 &lt;Response&gt;。
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Response")
@XmlAccessorType(XmlAccessType.FIELD)
public class CatalogResponseXml {

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

    public List<CatalogItem> getItems() {
        return deviceList != null ? deviceList.getItems() : null;
    }
}

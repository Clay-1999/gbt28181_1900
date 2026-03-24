package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GB/T 28181 Catalog 目录项（&lt;Item&gt;），用于 Catalog Notify 和 Catalog Response 的 DeviceList 解析。
 */
@Data
@NoArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
public class CatalogItem {

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "Name")
    private String name;

    @XmlElement(name = "Manufacturer")
    private String manufacturer;

    @XmlElement(name = "Model")
    private String model;

    @XmlElement(name = "Status")
    private String status;

    @XmlElement(name = "ParentID")
    private String parentId;

    @XmlElement(name = "Info")
    private Info info;

    /** 便捷方法：从 Info 中取 PTZType */
    public String getPtzType() {
        return info != null ? info.getPtzType() : null;
    }

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Info {
        /** 摄像机结构类型：1-球机;2-半球;3-固定枪机;4-遥控枪机;5-遥控半球;6-全景/拼接;7-分割通道 */
        @XmlElement(name = "PTZType")
        private String ptzType;
    }
}

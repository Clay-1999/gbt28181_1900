package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GB/T 28181 A.2.4.3 目录查询/订阅请求报文。
 *
 * <pre>{@code
 * <Query>
 *   <CmdType>Catalog</CmdType>
 *   <SN>12345</SN>
 *   <DeviceID>目标平台互联编码</DeviceID>
 * </Query>
 * }</pre>
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Query")
@XmlAccessorType(XmlAccessType.FIELD)
public class CatalogQueryXml {

    @XmlElement(name = "CmdType")
    private String cmdType = "Catalog";

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    public CatalogQueryXml(int sn, String deviceId) {
        this.sn = sn;
        this.deviceId = deviceId;
    }
}

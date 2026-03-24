package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GB/T 28181 ConfigDownload 查询报文。
 *
 * <pre>{@code
 * <Query>
 *   <CmdType>ConfigDownload</CmdType>
 *   <SN>12345</SN>
 *   <DeviceID>...</DeviceID>
 *   <ConfigType>OSDConfig</ConfigType>
 * </Query>
 * }</pre>
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Query")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConfigDownloadQuery {

    @XmlElement(name = "CmdType")
    private String cmdType = "ConfigDownload";

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "ConfigType")
    private String configType;

    public ConfigDownloadQuery(int sn, String deviceId, CameraConfigType configType) {
        this.sn = sn;
        this.deviceId = deviceId;
        this.configType = configType.name();
    }
}

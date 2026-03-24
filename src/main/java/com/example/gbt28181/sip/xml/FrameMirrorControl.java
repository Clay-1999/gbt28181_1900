package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GB/T 28181 DeviceConfig 镜像翻转下发报文。
 *
 * <pre>{@code
 * <Control>
 *   <CmdType>DeviceConfig</CmdType>
 *   <SN>12345</SN>
 *   <DeviceID>...</DeviceID>
 *   <FrameMirror>2</FrameMirror>
 * </Control>
 * }</pre>
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Control")
@XmlAccessorType(XmlAccessType.FIELD)
public class FrameMirrorControl {

    @XmlElement(name = "CmdType")
    private String cmdType = "DeviceConfig";

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "FrameMirror")
    private int frameMirror;
}

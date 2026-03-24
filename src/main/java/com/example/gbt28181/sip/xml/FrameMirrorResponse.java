package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/** GB/T 28181 ConfigDownload 响应报文（FrameMirror）。 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Response")
@XmlAccessorType(XmlAccessType.FIELD)
public class FrameMirrorResponse {

    @XmlElement(name = "CmdType")
    private String cmdType;

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "Result")
    private String result;

    @XmlElement(name = "FrameMirror")
    private Integer frameMirror;
}

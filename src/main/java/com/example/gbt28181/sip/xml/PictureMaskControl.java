package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GB/T 28181 DeviceConfig 视频遮挡下发报文。
 *
 * <pre>{@code
 * <Control>
 *   <CmdType>DeviceConfig</CmdType>
 *   <SN>12345</SN>
 *   <DeviceID>...</DeviceID>
 *   <PictureMask>
 *     <ON>1</ON>
 *   </PictureMask>
 * </Control>
 * }</pre>
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Control")
@XmlAccessorType(XmlAccessType.FIELD)
public class PictureMaskControl {

    @XmlElement(name = "CmdType")
    private String cmdType = "DeviceConfig";

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "PictureMask")
    private PictureMask pictureMask;

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PictureMask {

        @XmlElement(name = "ON")
        private int on;
    }
}

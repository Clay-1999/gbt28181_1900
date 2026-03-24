package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@XmlRootElement(name = "Control")
@XmlAccessorType(XmlAccessType.FIELD)
public class SnapShotControl {

    @XmlElement(name = "CmdType")
    private String cmdType = "DeviceConfig";

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "SnapShot")
    private SnapShot snapShot;

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SnapShot {
        /** 连拍张数，取值1~10 */
        @XmlElement(name = "SnapNum")
        private int snapNum;

        /** 单张抓拍间隔，单位秒，最短1秒（可选） */
        @XmlElement(name = "Interval")
        private Integer interval;

        /** 抓拍图像上传路径 */
        @XmlElement(name = "UploadURL")
        private String uploadURL;

        /** 会话ID，长度32~128字节 */
        @XmlElement(name = "SessionID")
        private String sessionID;
    }
}

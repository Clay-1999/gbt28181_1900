package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GB/T 28181 DeviceConfig 视频参数下发报文。
 *
 * <pre>{@code
 * <Control>
 *   <CmdType>DeviceConfig</CmdType>
 *   <SN>12345</SN>
 *   <DeviceID>...</DeviceID>
 *   <VideoParamAttribute>
 *     <Item>
 *       <StreamNumber>0</StreamNumber>
 *       <VideoFormat>2</VideoFormat>
 *       <Resolution>6</Resolution>
 *       <FrameRate>25</FrameRate>
 *       <BitRateType>2</BitRateType>
 *       <VideoBitRate>4096</VideoBitRate>
 *     </Item>
 *   </VideoParamAttribute>
 * </Control>
 * }</pre>
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Control")
@XmlAccessorType(XmlAccessType.FIELD)
public class VideoParamAttributeControl {

    @XmlElement(name = "CmdType")
    private String cmdType = "DeviceConfig";

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElementWrapper(name = "VideoParamAttribute")
    @XmlElement(name = "Item")
    private List<StreamItem> streamInfoList;

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class StreamItem {

        @XmlElement(name = "StreamNumber")
        private int streamNumber;

        @XmlElement(name = "VideoFormat")
        private int videoFormat;

        @XmlElement(name = "Resolution")
        private String resolution;

        @XmlElement(name = "FrameRate")
        private int frameRate;

        @XmlElement(name = "BitRateType")
        private int bitRateType;

        @XmlElement(name = "VideoBitRate")
        private int videoBitRate;
    }
}

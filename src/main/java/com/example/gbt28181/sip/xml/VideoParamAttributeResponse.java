package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GB/T 28181 ConfigDownload 响应报文（VideoParamAttribute）。
 *
 * <pre>{@code
 * <Response>
 *   <CmdType>ConfigDownload</CmdType>
 *   <SN>12345</SN>
 *   <DeviceID>...</DeviceID>
 *   <Result>OK</Result>
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
 * </Response>
 * }</pre>
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Response")
@XmlAccessorType(XmlAccessType.FIELD)
public class VideoParamAttributeResponse {

    @XmlElement(name = "CmdType")
    private String cmdType;

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "Result")
    private String result;

    @XmlElementWrapper(name = "VideoParamAttribute")
    @XmlElement(name = "Item")
    private List<StreamItem> items;

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

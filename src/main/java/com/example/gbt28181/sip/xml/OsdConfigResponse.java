package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@XmlRootElement(name = "Response")
@XmlAccessorType(XmlAccessType.FIELD)
public class OsdConfigResponse {

    @XmlElement(name = "CmdType")
    private String cmdType;

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "Result")
    private String result;

    @XmlElement(name = "OSDConfig")
    private OsdConfig osdConfig;

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class OsdConfig {
        /** 图像宽度（像素） */
        @XmlElement(name = "Length")
        private Integer length;

        /** 图像高度（像素） */
        @XmlElement(name = "Width")
        private Integer width;

        /** 时间 OSD 左上角 X 坐标 */
        @XmlElement(name = "TimeX")
        private Integer timeX;

        /** 时间 OSD 左上角 Y 坐标 */
        @XmlElement(name = "TimeY")
        private Integer timeY;

        /** 时间显示开关：0-关闭，1-打开 */
        @XmlElement(name = "TimeEnable")
        private int timeEnable;

        /** 时间显示格式：0-年月日时分秒，1-月日时分秒 */
        @XmlElement(name = "TimeType")
        private Integer timeType;

        /** 字幕总数 */
        @XmlElement(name = "SumNum")
        private Integer sumNum;

        /** 文字叠加总开关：0-关闭，1-打开 */
        @XmlElement(name = "TextEnable")
        private int textEnable;

        /** 字幕列表 */
        @XmlElement(name = "Item")
        private List<OsdItem> items;
    }

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class OsdItem {
        @XmlElement(name = "Text")
        private String text;

        @XmlElement(name = "X")
        private int x;

        @XmlElement(name = "Y")
        private int y;
    }
}

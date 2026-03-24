package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GB/T 28181 DeviceControl 消息体 JAXB 类（云台控制用）。
 *
 * <pre>
 * &lt;Control&gt;
 *   &lt;CmdType&gt;DeviceControl&lt;/CmdType&gt;
 *   &lt;SN&gt;...&lt;/SN&gt;
 *   &lt;DeviceID&gt;...&lt;/DeviceID&gt;
 *   &lt;PTZCmd&gt;A50F0008000000B0&lt;/PTZCmd&gt;
 *   &lt;PTZCmdParams&gt;...&lt;/PTZCmdParams&gt;  &lt;!-- 可选 --&gt;
 * &lt;/Control&gt;
 * </pre>
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Control")
@XmlAccessorType(XmlAccessType.FIELD)
public class DeviceControlXml {

    @XmlElement(name = "CmdType")
    private String cmdType = "DeviceControl";

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    /** 8 字节 PTZCmd 十六进制字符串，详见附录 A.3。 */
    @XmlElement(name = "PTZCmd")
    private String ptzCmd;

    /** 可选附加参数（预置位名称 / 巡航轨迹名称）。 */
    @XmlElement(name = "PTZCmdParams")
    private PtzCmdParams ptzCmdParams;

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PtzCmdParams {
        @XmlElement(name = "PresetName")
        private String presetName;
        @XmlElement(name = "CruiseTrackName")
        private String cruiseTrackName;
    }
}

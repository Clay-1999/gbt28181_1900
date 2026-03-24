package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 预置位查询应答（A.2.6.10）。
 * <pre>
 * &lt;Response&gt;
 *   &lt;CmdType&gt;PresetQuery&lt;/CmdType&gt;
 *   &lt;SN&gt;...&lt;/SN&gt;
 *   &lt;DeviceID&gt;...&lt;/DeviceID&gt;
 *   &lt;SumNum&gt;2&lt;/SumNum&gt;
 *   &lt;PresetList Num="2"&gt;
 *     &lt;Item&gt;&lt;PresetID&gt;1&lt;/PresetID&gt;&lt;PresetName&gt;大门&lt;/PresetName&gt;&lt;/Item&gt;
 *   &lt;/PresetList&gt;
 * &lt;/Response&gt;
 * </pre>
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Response")
@XmlAccessorType(XmlAccessType.FIELD)
public class PresetListResponse {

    @XmlElement(name = "CmdType")
    private String cmdType;

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "SumNum")
    private int sumNum;

    @XmlElement(name = "PresetList")
    private PresetList presetList;

    public List<PresetItem> getItems() {
        return presetList != null ? presetList.getItems() : List.of();
    }

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PresetList {
        @XmlAttribute(name = "Num")
        private int num;

        @XmlElement(name = "Item")
        private List<PresetItem> items;
    }

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PresetItem {
        @XmlElement(name = "PresetID")
        private String presetId;
        @XmlElement(name = "PresetName")
        private String presetName;
    }
}

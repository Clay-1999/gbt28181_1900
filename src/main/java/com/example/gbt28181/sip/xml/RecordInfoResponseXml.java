package com.example.gbt28181.sip.xml;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文件目录检索应答（GB/T 28181 A.2.6.7）。
 */
@Data
@NoArgsConstructor
@XmlRootElement(name = "Response")
@XmlAccessorType(XmlAccessType.FIELD)
public class RecordInfoResponseXml {

    @XmlElement(name = "CmdType")
    private String cmdType;

    @XmlElement(name = "SN")
    private int sn;

    @XmlElement(name = "DeviceID")
    private String deviceId;

    @XmlElement(name = "Name")
    private String name;

    @XmlElement(name = "SumNum")
    private int sumNum;

    @XmlElement(name = "RecordList")
    private RecordList recordList;

    public List<RecordItem> getItems() {
        return recordList != null && recordList.getItems() != null ? recordList.getItems() : List.of();
    }

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class RecordList {
        @XmlAttribute(name = "Num")
        private int num;

        @XmlElement(name = "Item")
        private List<RecordItem> items;
    }

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class RecordItem {
        @XmlElement(name = "DeviceID")
        private String deviceId;

        @XmlElement(name = "Name")
        private String name;

        @XmlElement(name = "FilePath")
        private String filePath;

        @XmlElement(name = "Address")
        private String address;

        @XmlElement(name = "StartTime")
        private String startTime;

        @XmlElement(name = "EndTime")
        private String endTime;

        @XmlElement(name = "Secrecy")
        private Integer secrecy;

        @XmlElement(name = "Type")
        private String type;

        @XmlElement(name = "RecorderID")
        private String recorderId;

        @XmlElement(name = "FileSize")
        private String fileSize;

        @XmlElement(name = "StreamNumber")
        private Integer streamNumber;
    }
}

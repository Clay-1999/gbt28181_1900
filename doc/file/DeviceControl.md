# A.2.3 控制命令

## A.2.3.1 设备控制命令

### A.2.3.1.1 设备控制命令消息体

设备控制命令消息体 XML Schema 定义如下：

```xml
<element name="Control">
  <complexType>
    <sequence>
      <!-- 命令类型：设备控制（必选） -->
      <element name="CmdType" fixed="DeviceControl"/>
      <!-- 命令序列号（必选） -->
      <element name="SN" type="tg:SNType"/>
      <!-- 目标设备编码（必选） -->
      <element name="DeviceID" type="tg:deviceIDType"/>
      <!-- 设备控制请求命令序列见 A.2.3.1.2 ~ A.2.3.1.14 -->
      <!-- 扩展信息，可多项 -->
      <element name="ExtraInfo" minOccurs="0" maxOccurs="unbounded">
        <simpleType>
          <restriction base="string">
            <maxLength value="1024"/>
          </restriction>
        </simpleType>
      </element>
    </sequence>
  </complexType>
</element>
```

---

### A.2.3.1.2 摄像机云台控制命令

```xml
<!-- 摄像机云台控制命令（可选，控制码应符合附录 A.3 的规定） -->
<element name="PTZCmd" type="tg:PTZCmdType" minOccurs="0"/>

<!-- 摄像机云台控制命令附加参数（可选） -->
<element name="PTZCmdParams" minOccurs="0">
  <complexType>
    <sequence>
      <!-- 预置位名称（PTZCmd 为设置预置位命令时可选） -->
      <element name="PresetName" type="string" minOccurs="0"/>
      <!-- 巡航轨迹名称（最长 32 字节，PTZCmd 为巡航指令命令时可选） -->
      <element name="CruiseTrackName" type="string" minOccurs="0"/>
    </sequence>
  </complexType>
</element>
```

---

### A.2.3.1.3 远程启动控制命令

```xml
<!-- 远程启动控制命令（可选） -->
<element name="TeleBoot" minOccurs="0">
  <simpleType>
    <restriction base="string">
      <enumeration value="Boot"/>
    </restriction>
  </simpleType>
</element>
```

---

### A.2.3.1.4 录像控制命令

```xml
<!-- 录像控制命令（可选） -->
<element name="RecordCmd" type="tg:recordType" minOccurs="0"/>
<!-- 码流类型：0-主码流，1-子码流1，2-子码流2，以此类推（可选，缺省为 0） -->
<element name="StreamNumber" type="integer"/>
```

---

### A.2.3.1.5 报警布防/撤防控制命令

```xml
<!-- 报警布防/撤防命令（可选） -->
<element name="GuardCmd" type="tg:guardType" minOccurs="0"/>
```

---

### A.2.3.1.6 报警复位控制命令

```xml
<!-- 报警复位命令（可选） -->
<element name="AlarmCmd" minOccurs="0">
  <simpleType>
    <restriction base="string">
      <enumeration value="ResetAlarm"/>
    </restriction>
  </simpleType>
</element>

<!-- 报警复位控制时，扩展此项，携带报警方式、报警类型 -->
<element name="Info" minOccurs="0">
  <complexType>
    <sequence>
      <!--
        复位报警的报警方式属性，取值：
          0-全部；1-电话报警；2-设备报警；3-短信报警；
          4-GPS 报警；5-视频报警；6-设备故障报警；7-其他报警；
          可组合，如 "1/2" 表示电话报警或设备报警
      -->
      <element name="AlarmMethod" type="string" minOccurs="0"/>
      <!--
        复位报警的报警类型属性：
        报警方式为 2 时：
          1-视频丢失报警；2-设备防拆报警；3-存储设备磁盘满报警；
          4-设备高温报警；5-设备低温报警
        报警方式为 5 时：
          1-人工视频报警；2-运动目标检测报警；3-遗留物检测报警；
          4-物体移除检测报警；5-绊线检测报警；6-入侵检测报警；
          7-逆行检测报警；8-徘徊检测报警；9-流量统计报警；
          10-密度检测报警；11-视频异常检测报警；12-快速移动报警；
          13-图像遮挡报警
        报警方式为 6 时：
          1-存储设备磁盘故障报警；2-存储设备风扇故障报警
      -->
      <element name="AlarmType" type="string" minOccurs="0"/>
    </sequence>
  </complexType>
</element>
```

---

### A.2.3.1.7 强制关键帧控制命令

```xml
<!-- 强制关键帧命令，设备收到此命令应立刻发送一个 IDR 帧（可选） -->
<element name="IFrameCmd" minOccurs="0">
  <simpleType>
    <restriction base="string">
      <enumeration value="Send"/>
    </restriction>
  </simpleType>
</element>
```

---

### A.2.3.1.8 拉框放大控制命令

```xml
<!-- 拉框放大控制命令（可选） -->
<element name="DragZoomIn" minOccurs="0">
  <complexType>
    <sequence>
      <!-- 播放窗口长度像素值（必选） -->
      <element name="Length" type="integer"/>
      <!-- 播放窗口宽度像素值（必选） -->
      <element name="Width" type="integer"/>
      <!-- 拉框中心的横轴坐标像素值（必选） -->
      <element name="MidPointX" type="integer"/>
      <!-- 拉框中心的纵轴坐标像素值（必选） -->
      <element name="MidPointY" type="integer"/>
      <!-- 拉框长度像素值（必选） -->
      <element name="LengthX" type="integer"/>
      <!-- 拉框宽度像素值（必选） -->
      <element name="LengthY" type="integer"/>
    </sequence>
  </complexType>
</element>
```

---

### A.2.3.1.9 拉框缩小控制命令

```xml
<!-- 拉框缩小控制命令（可选） -->
<element name="DragZoomOut" minOccurs="0">
  <complexType>
    <sequence>
      <!-- 播放窗口长度像素值（必选） -->
      <element name="Length" type="integer"/>
      <!-- 播放窗口宽度像素值（必选） -->
      <element name="Width" type="integer"/>
      <!-- 拉框中心的横轴坐标像素值（必选） -->
      <element name="MidPointX" type="integer"/>
      <!-- 拉框中心的纵轴坐标像素值（必选） -->
      <element name="MidPointY" type="integer"/>
      <!-- 拉框长度像素值（必选） -->
      <element name="LengthX" type="integer"/>
      <!-- 拉框宽度像素值（必选） -->
      <element name="LengthY" type="integer"/>
    </sequence>
  </complexType>
</element>
```

> **注：** 拉框放大命令将播放窗口选定框内的图像放大到整个播放窗口；拉框缩小命令将整个播放窗口的图像缩小到播放窗口选定框内。命令中的坐标系以播放窗口的左上角为原点，各坐标取值以像素为单位。

---

### A.2.3.1.10 看守位控制命令

```xml
<!-- 看守位控制命令（可选） -->
<element name="HomePosition" minOccurs="0">
  <complexType>
    <sequence>
      <!-- 看守位使能：1-开启；0-关闭（必选） -->
      <element name="Enabled" type="integer"/>
      <!-- 自动归位时间间隔，开启看守位时使用，单位：秒（可选） -->
      <element name="ResetTime" type="integer" minOccurs="0"/>
      <!-- 调用预置位编号，开启看守位时使用，取值范围 0~255（可选） -->
      <element name="PresetIndex" minOccurs="0">
        <simpleType>
          <restriction base="integer">
            <minInclusive value="0"/>
            <maxInclusive value="255"/>
          </restriction>
        </simpleType>
      </element>
    </sequence>
  </complexType>
</element>
```

---

### A.2.3.1.11 PTZ 精准控制命令

```xml
<!-- PTZ 精准控制命令（可选） -->
<element name="PTZPreciseCtrl" type="tg:PTZPreciseCtrlType" minOccurs="0"/>
```

---

### A.2.3.1.12 设备软件升级控制命令

```xml
<!-- 设备软件升级命令（可选） -->
<element name="DeviceUpgrade" minOccurs="0">
  <complexType>
    <!-- 设备固件版本（必选） -->
    <element name="Firmware" type="string"/>
    <!-- 升级文件的完整路径（必选） -->
    <element name="FileURL" type="string"/>
    <!-- 设备厂商（必选） -->
    <element name="Manufacturer" type="string"/>
    <!--
      会话 ID，由平台生成，用于关联升级流程多个命令的会话标识（必选）。
      由大小写英文字母、数字、短划线组成，长度 32~128 字节。
    -->
    <element name="SessionID">
      <simpleType>
        <restriction base="string">
          <minLength value="32"/>
          <maxLength value="128"/>
        </restriction>
      </simpleType>
    </element>
  </complexType>
</element>
```

---

### A.2.3.1.13 存储卡格式化控制命令

```xml
<!-- 存储卡格式化命令（可选）。SD 卡编号从 1 开始；值为 0 时对所有存储卡格式化 -->
<element name="FormatSDCard" minOccurs="0">
  <simpleType>
    <restriction base="integer">
      <minInclusive value="0"/>
    </restriction>
  </simpleType>
</element>
```

---

### A.2.3.1.14 目标跟踪控制命令

全景摄像机球机画面中目标的自动及手动跟踪控制：

- **手动跟踪**：平台将全景画面上框选目标的坐标发送给设备，设备中的球机根据坐标执行跟踪。由于平台与设备画面比例不同，需进行比例关系转化，平台需提供播放窗口长宽像素值。
- **自动跟踪**：平台发送命令后设备根据已配置参数自动执行跟踪，无需平台下发坐标参数。

```xml
<!--
  目标跟踪命令（可选）：
    "Auto"   - 自动跟踪
    "Manual" - 手动跟踪（指哪打哪），携带全景图片中框选的区域坐标信息
    "Stop"   - 停止跟踪
  SN 后面的目标设备编码（必选）指全景相机的球机通道。
-->
<element name="TargetTrack" minOccurs="0">
  <simpleType>
    <restriction base="string">
      <enumeration value="Auto"/>
      <enumeration value="Manual"/>
      <enumeration value="Stop"/>
    </restriction>
  </simpleType>
</element>

<!-- 目标设备编码（可选），指全景相机中的全景通道 ID -->
<element name="DeviceID2" type="tg:deviceIDType" minOccurs="0"/>

<!-- 全景图片大小及框选区域坐标信息（可选，手动跟踪时需要） -->
<element name="TargetArea" minOccurs="0">
  <complexType>
    <sequence>
      <!-- 全景播放窗口长度像素值（必选） -->
      <element name="Length" type="integer"/>
      <!-- 全景播放窗口宽度像素值（必选） -->
      <element name="Width" type="integer"/>
      <!-- 跟踪框中心的横轴坐标像素值（必选） -->
      <element name="MidPointX" type="integer"/>
      <!-- 跟踪框中心的纵轴坐标像素值（必选） -->
      <element name="MidPointY" type="integer"/>
      <!-- 跟踪框长度像素值（必选） -->
      <element name="LengthX" type="integer"/>
      <!-- 跟踪框宽度像素值（必选） -->
      <element name="LengthY" type="integer"/>
    </sequence>
  </complexType>
</element>
```

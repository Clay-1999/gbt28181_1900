# A.2.4 查询命令

## A.2.4.8 设备预置位查询

```xml
<!-- 命令类型：预置位查询（必选） -->
<element name="CmdType" fixed="PresetQuery"/>
<!-- 命令序列号（必选） -->
<element name="SN" type="tg:SNType"/>
<!-- 查询目标设备编码（必选） -->
<element name="DeviceID" type="tg:deviceIDType"/>
```

---

## A.2.4.9 移动设备位置数据订阅

```xml
<!-- 命令类型：移动设备位置数据查询（必选） -->
<element name="CmdType" fixed="MobilePosition"/>
<!-- 命令序列号（必选） -->
<element name="SN" type="tg:SNType"/>
<!-- 查询移动设备/系统编码（必选） -->
<element name="DeviceID" type="tg:deviceIDType"/>
<!-- 移动设备位置信息上报时间间隔，单位：秒，默认值 5（可选） -->
<element name="Interval" type="integer" default="5"/>
```

---

## A.2.4.10 看守位信息查询

```xml
<!-- 命令类型：看守位信息查询（必选） -->
<element name="CmdType" fixed="HomePositionQuery"/>
<!-- 命令序列号（必选） -->
<element name="SN" type="tg:SNType"/>
<!-- 目标设备编码（必选） -->
<element name="DeviceID" type="tg:deviceIDType"/>
```

---

## A.2.4.11 巡航轨迹列表查询

```xml
<!-- 命令类型：巡航轨迹列表查询（必选） -->
<element name="CmdType" fixed="CruiseTrackListQuery"/>
<!-- 命令序列号（必选） -->
<element name="SN" type="tg:SNType"/>
<!-- 目标设备编码（必选） -->
<element name="DeviceID" type="tg:deviceIDType"/>
```

---

## A.2.4.12 巡航轨迹查询

```xml
<!-- 命令类型：巡航轨迹查询（必选） -->
<element name="CmdType" fixed="CruiseTrackQuery"/>
<!-- 命令序列号（必选） -->
<element name="SN" type="tg:SNType"/>
<!-- 目标设备编码（必选） -->
<element name="DeviceID" type="tg:deviceIDType"/>
<!-- 轨迹编号（必选）：0-第一条轨迹；1-第二条轨迹 -->
<element name="Number" type="integer"/>
```

---

## A.2.4.13 PTZ 精准状态查询或订阅

```xml
<!-- 命令类型：PTZ 精准状态查询（必选） -->
<element name="CmdType" fixed="PTZPosition"/>
<!-- 命令序列号（必选） -->
<element name="SN" type="tg:SNType"/>
<!-- 查询目标设备编码（必选） -->
<element name="DeviceID" type="tg:deviceIDType"/>
```

---

## A.2.4.14 存储卡状态查询

```xml
<!-- 命令类型：存储卡状态查询（可选） -->
<element name="CmdType" fixed="SDCardStatus" minOccurs="0"/>
<!-- 命令序列号（必选） -->
<element name="SN" type="tg:SNType"/>
<!-- 查询目标设备编码（必选） -->
<element name="DeviceID" type="tg:deviceIDType"/>
```

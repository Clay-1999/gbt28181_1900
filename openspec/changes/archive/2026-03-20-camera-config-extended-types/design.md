## Context

4 种扩展配置类型与已有的 4 种（VideoParamAttribute、OSDConfig、PictureMask、FrameMirror）采用完全相同的实现模式：
- JAXB Response 类（反序列化 IVS1900 回复）+ Control 类（序列化下发报文）
- `Ivs1900SipConfigService.parseConfigXml` + `buildDeviceConfigXml` 各加一个 switch case
- `RemoteDeviceConfigService` 镜像相同结构
- `DeviceController` 新增对应 GET/PUT endpoint
- 前端 `DevicesView.vue` 配置对话框新增 tab + 表单

本次同步引入两项架构改进：
1. **configType 枚举化**：将所有字符串字面量（`"VideoParamAttribute"` 等）替换为枚举 `CameraConfigType`，消除魔法字符串，编译期保证合法性
2. **XML 全注解化**：现有 `queryConfig` 中手动拼接的 ConfigDownload Query XML 改为 JAXB 注解类 `ConfigDownloadQuery`，保持与 Response/Control 类一致的编写规范

## Goals / Non-Goals

**Goals:**
- 实现 VideoRecordPlan、VideoAlarmRecord、AlarmReport、SnapShot 4 种配置类型的完整后端 + 前端支持
- 引入 `CameraConfigType` 枚举，覆盖全部 8 种配置类型
- XML 组装和解析全部使用 JAXB 注解，不允许手动字符串拼接

**Non-Goals:**
- 不修改 SIP 协议层（GbtSipListener、ConfigDownloadHandler 已支持这些类型的透传）
- 不新增数据库存储

## Decisions

### 枚举设计 CameraConfigType

```java
public enum CameraConfigType {
    VideoParamAttribute("video-param"),
    OSDConfig("osd"),
    PictureMask("picture-mask"),
    FrameMirror("frame-mirror"),
    VideoRecordPlan("video-record-plan"),
    VideoAlarmRecord("video-alarm-record"),
    AlarmReport("alarm-report"),
    SnapShot("snap-shot");

    private final String urlSegment; // URL path segment（与前端 tab name 对应）
}
```

- `urlSegment` 与 DeviceController 的 path 段对应，通过 `fromUrlSegment(String)` 解析请求参数
- `name()` 即 GB/T 28181 XML 中 `<ConfigType>` 的值

### XML 全注解化

新增 `ConfigDownloadQuery.java`（`@XmlRootElement(name = "Query")`），字段：`CmdType="ConfigDownload"`、`SN`、`DeviceID`、`ConfigType`（String，值为 `CameraConfigType.name()`）。

`Ivs1900SipConfigService.queryConfig()` 和 `RemoteDeviceMessageForwarder.queryConfig()` 中手动拼接的 XML 改为 `GbXmlMapper.toXml(query)`。

### 4 种类型的字段设计（基于 GB/T 28181-2022）

**VideoRecordPlan（录像计划）**
- `recordMethod`: int，录像方式（0=定时录像，1=移动侦测，2=报警触发）
- `streamType`: int，录像码流（0=主码流，1=辅码流）

**VideoAlarmRecord（报警录像）**
- `preRecordTime`: int，预录时间（秒，0-30）
- `alarmRecordTime`: int，报警录像时间（秒，10-300）

**AlarmReport（报警上报）**
- `alarmMethod`: int，报警方式（位运算，Bit1=电话报警，Bit2=设备主动上报，Bit3=中心查询）
- `alarmRecordTime`: int，报警录像时间（秒）
- `preRecordTime`: int，预录时间（秒）

**SnapShot（抓图）**
- `snapShotInterval`: int，抓图间隔（秒）
- `snapShotTimes`: int，连拍次数

### JAXB 类命名
- Response 类：`VideoRecordPlanResponse`、`VideoAlarmRecordResponse`、`AlarmReportResponse`、`SnapShotResponse`
- Control 类：`VideoRecordPlanControl`、`VideoAlarmRecordControl`、`AlarmReportControl`、`SnapShotControl`
- XML 根元素：Response 类用 `@XmlRootElement(name = "Response")`，Control 类用 `@XmlRootElement(name = "Control")`
- 配置子元素：与配置类型同名（如 `<VideoRecordPlan>`）

### 前端 URL 路径（与枚举 urlSegment 对应）
- `video-record-plan`、`video-alarm-record`、`alarm-report`、`snap-shot`

## Risks / Trade-offs
- [IVS1900 实际支持哪些字段] → JAXB 字段均设为可选，缺失字段返回默认值 0
- [已有手动 XML 拼接] → `ConfigDownloadQuery` 统一替换，`DeviceConfig` 已全部走 JAXB Control 类，无遗漏


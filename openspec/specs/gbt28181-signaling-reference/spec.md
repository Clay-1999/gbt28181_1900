## ADDED Requirements

### Requirement: GB/T 28181-2022 信令参考文档
系统文档中应包含一份完整的参考资料，覆盖 GB/T 28181-2022 标准定义的所有信令交互，包括 SIP 方法、MANSCDP XML CmdType 取值及第 9 章各小节的交互流程。

#### Scenario: 开发者可以快速定位任意操作对应的 SIP 方法
- **WHEN** 开发者需要实现或排查某个信令操作
- **THEN** 参考文档明确说明该操作所需的 SIP 方法（REGISTER/INVITE/MESSAGE/NOTIFY/SUBSCRIBE/INFO/BYE）及 XML CmdType

#### Scenario: 参考文档覆盖第 9 章全部信令类别
- **WHEN** 开发者阅读参考文档
- **THEN** 文档涵盖全部 14 个类别：设备注册（9.1）、实时视音频（9.2）、设备控制（9.3）、报警（9.4）、设备信息查询（9.5）、心跳（9.6）、录像查询（9.7）、历史回放（9.8）、文件下载（9.9）、校时（9.10）、订阅/通知（9.11）、语音广播对讲（9.12）、软件升级（9.13）、图像抓拍（9.14）

---

# GB/T 28181-2022 信令参考

## 概述

GB/T 28181-2022 以 SIP 作为信令协议。控制与查询命令通过 **SIP MESSAGE** 携带 MANSCDP XML 消息体（Content-Type: Application/MANSCDP+xml）传送；媒体会话通过 **SIP INVITE/ACK/BYE** 配合 SDP 协商；事件订阅依照 RFC 6665 使用 **SIP SUBSCRIBE/NOTIFY**。

### SIP 方法汇总

| 方法 | 用途 |
|------|------|
| REGISTER | 设备向平台注册（含级联平台间注册） |
| MESSAGE | 所有 MANSCDP 控制/查询命令（报警、查询、配置、PTZ 等） |
| INVITE | 媒体会话建立（实时流/回放/下载/对讲） |
| ACK | 完成 INVITE 三次握手 |
| BYE | 终止媒体会话 |
| INFO | 回放会话内的控制命令（快进、暂停、定位） |
| SUBSCRIBE | 事件订阅（报警事件、移动位置、PTZ 位置、目录） |
| NOTIFY | 订阅源主动推送事件通知 |

### MANSCDP XML CmdType 取值

所有 SIP MESSAGE 消息体的 Content-Type 均为 `Application/MANSCDP+xml`，根元素为 `<Query>`、`<Control>`、`<Notify>` 或 `<Response>` 之一。

**查询命令（平台 → 设备，设备返回 Response MESSAGE）：**

| CmdType | 方向 | 说明 |
|---------|------|------|
| DeviceInfo | 平台 → 设备 | 查询设备信息（型号、固件版本等） |
| DeviceStatus | 平台 → 设备 | 查询设备状态（在线/离线、录像状态） |
| Catalog | 平台 → 设备 | 查询设备目录（通道列表） |
| RecordInfo | 平台 → 设备 | 查询录像文件 |
| Alarm | 平台 → 设备 | 订阅报警通知（§9.4 / A.2.4.6，使用 MESSAGE 而非 SUBSCRIBE） |
| ConfigDownload | 平台 → 设备 | 查询设备配置参数 |
| PresetQuery | 平台 → 设备 | 查询 PTZ 预置位 |
| GroupInfo | 平台 → 设备 | 查询设备分组信息 |
| MobilePosition | 平台 → 设备 | 查询移动设备位置 |

**控制命令（平台 → 设备，响应可选）：**

| CmdType | 方向 | 说明 |
|---------|------|------|
| PTZCmd | 平台 → 设备 | PTZ 云台控制（PELCO-D 编码） |
| DeviceControl | 平台 → 设备 | 设备控制（录像、布撤防、雨刷等） |
| DeviceConfig | 平台 → 设备 | 下发设备配置参数 |
| GuardCmd | 平台 → 设备 | 布防/撤防 |
| RecordCmd | 平台 → 设备 | 开始/停止录像 |
| TeleBoot | 平台 → 设备 | 远程重启 |
| DragZoomIn/Out | 平台 → 设备 | 电子放大/缩小 |
| DeviceUpgrade | 平台 → 设备 | OTA 固件升级 |
| SnapShotConfig | 平台 → 设备 | 远程图像抓拍 |

**通知/上报命令（设备 → 平台）：**

| CmdType | 方向 | 说明 |
|---------|------|------|
| Keepalive | 设备 → 平台 | 心跳（每 60 秒一次） |
| Alarm | 设备 → 平台 | 报警通知 |
| MobilePosition | 设备 → 平台 | GPS 位置上报 |
| Catalog | 设备 → 平台 | 目录变更推送 |
| MediaStatus | 设备 → 平台 | 媒体流状态上报 |
| Broadcast | 平台 → 设备 | 语音广播启动通知 |

---

## 9.1 设备注册

**SIP 方法：** REGISTER
**方向：** 设备 → 平台（级联时也存在平台 → 平台）
**认证：** Digest 认证（WWW-Authenticate / Authorization 头域）

### 交互流程

```
设备                        平台
  |                           |
  |--- REGISTER ------------->|  （无认证信息）
  |<-- 401 Unauthorized ------|  （WWW-Authenticate: Digest realm=..., nonce=...）
  |--- REGISTER ------------->|  （Authorization: Digest username=..., response=...）
  |<-- 200 OK ----------------|  （Date 头域用于校时，见 §9.10）
```

### 关键参数

- `From` / `To`：`sip:<设备编号>@<域>`
- `Expires`：3600（1小时）；注销时 `Expires: 0`
- `Contact`：`sip:<设备编号>@<IP>:<端口>`
- 认证算法：MD5（RFC 2617）

### 实现状态
- ✅ 服务端：`SipRegistrationServer.java` — 接收下级设备的 REGISTER
- ✅ 客户端：`SipRegistrationClient.java` — 向上级平台发送 REGISTER（级联）

---

## 9.2 实时视音频点播

**SIP 方法：** INVITE / ACK / BYE
**方向：** 平台 → 设备
**媒体：** RTP over UDP/TCP，视频 H.264/H.265，音频 G.711/AAC

### 交互流程

```
平台                        设备
  |                           |
  |--- INVITE (SDP offer) --->|  （Subject: <设备ID>:0,<平台ID>:0）
  |<-- 100 Trying ------------|
  |<-- 200 OK (SDP answer) ---|
  |--- ACK ------------------>|
  |<======== RTP/RTCP ========|  （设备 → 平台单向或双向）
  |--- BYE ------------------>|
  |<-- 200 OK ----------------|
```

### SDP 关键字段

- `s=Play`（实时）/ `s=Playback`（回放）/ `s=Download`（文件下载）
- `y=`：SSRC（10位，GB/T 28181 强制要求）
- `f=`：视音频编码描述
- `m=video <端口> RTP/AVP 96`（或 `TCP/RTP/AVP`）

### 实现状态
- ✅ `SipInviteService.java` — 向设备发送 INVITE
- ✅ `StreamSession.java` / `StreamSessionStore.java` — 会话跟踪

---

## 9.3 设备控制

**SIP 方法：** MESSAGE（MANSCDP）
**方向：** 平台 → 设备
**响应：** 设备先回 200 OK；部分控制类型还会返回 Response MESSAGE

### PTZ 控制

```xml
<Control>
  <CmdType>PTZCmd</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
  <PTZCmd>A50F01027F007F00</PTZCmd>  <!-- PELCO-D 编码十六进制 -->
</Control>
```

无需等待 Response MESSAGE（200 OK 后即认为成功）。

### 设备控制（DeviceControl）

```xml
<Control>
  <CmdType>DeviceControl</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
  <RecordCmd>Record</RecordCmd>   <!-- 可选：StopRecord、Reboot 等 -->
</Control>
```

设备返回 `<Response><CmdType>DeviceControl</CmdType><Result>OK</Result></Response>`。

### 布撤防命令（GuardCmd）

```xml
<Control>
  <CmdType>GuardCmd</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
  <GuardCmd>SetGuard</GuardCmd>  <!-- 或：ResetGuard -->
</Control>
```

### 实现状态
- ✅ `PtzService.java` + `PtzCmdEncoder.java` — PTZ 控制
- ✅ `DeviceCommandRouter.java` — 路由入站控制命令

---

## 9.4 报警

### 9.4.1 报警通知（设备 → 平台）

**SIP 方法：** MESSAGE（MANSCDP）
**方向：** 设备 → 平台

```xml
<Notify>
  <CmdType>Alarm</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
  <AlarmPriority>1</AlarmPriority>  <!-- 1=一级 2=二级 3=三级 4=四级 -->
  <AlarmMethod>5</AlarmMethod>       <!-- 见下表 -->
  <AlarmTime>2022-06-01T10:00:00</AlarmTime>
  <AlarmDescription>入侵检测</AlarmDescription>
  <Longitude>116.397</Longitude>    <!-- 可选 GPS -->
  <Latitude>39.916</Latitude>
  <Info>
    <AlarmType>1</AlarmType>        <!-- 类型含义随 AlarmMethod 变化，见下表 -->
  </Info>
</Notify>
```

**AlarmMethod 取值：**

| 值 | 含义 |
|----|------|
| 0 | 全部 |
| 1 | 电话报警 |
| 2 | 设备报警 |
| 3 | 短信报警 |
| 4 | GPS 报警 |
| 5 | 视频报警（视频 AI） |
| 6 | 设备故障报警 |
| 7 | 其他报警 |

**AlarmType 取值（AlarmMethod=2，设备报警）：**

| 值 | 含义 |
|----|------|
| 1 | 视频丢失 |
| 2 | 设备防拆 |
| 3 | 存储介质满 |
| 4 | 低电量 |
| 5 | 市电掉电 |
| 6 | 视频遮挡 |

**AlarmType 取值（AlarmMethod=5，视频/AI 报警）：**

| 值 | 含义 |
|----|------|
| 1 | 运动目标检测 |
| 2 | 遗留物检测 |
| 3 | 物体移除检测 |
| 4 | 绊线检测 |
| 5 | 入侵检测 |
| 6 | 徘徊检测 |
| 7 | 流量统计 |
| 8 | 密度检测 |
| 9 | 视频异常检测 |
| 10 | 快速移动 |

**AlarmType 取值（AlarmMethod=6，设备故障报警）：**

| 值 | 含义 |
|----|------|
| 1 | 存储设备不存在 |
| 2 | 存储读写错误 |
| 3 | 存储容量不足 |
| 4 | 网络中断 |
| 5 | IP 冲突 |
| 6 | 非法访问 |
| 7 | 视频信号异常 |
| 8 | 编码设备分辨率不匹配 |

### 9.4.2 报警订阅（平台 → 设备）

**SIP 方法：** MESSAGE（MANSCDP）— **注意：不是 RFC 3265 SIP SUBSCRIBE**
**方向：** 平台 → 设备
**标准章节：** A.2.4.6

```xml
<Query>
  <CmdType>Alarm</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
</Query>
```

设备对 MESSAGE 回复 200 OK。此后设备将以 Notify MESSAGE 上报报警事件。

**重要说明：** 这是"查询式"订阅（一次性 MESSAGE），不是 RFC 3265 的 SUBSCRIBE 对话。订阅状态由平台侧内存维护，无 `Expires` 或续约机制——需续约时重新发送即可。

### 实现状态
- ✅ `AlarmSubscribeService.java` — 通过 `SipMessageSender` 发送订阅 MESSAGE
- ✅ `AlarmNotifyHandler.java` — 接收并解析入站报警 NOTIFY MESSAGE（支持 GB2312 解码）
- ✅ `AlarmEvent.java` / `AlarmEventRepository.java` — 报警事件持久化
- ✅ `AlarmController.java` — REST 接口 `GET /api/alarms`
- ✅ `AlarmView.vue` — 前端报警列表界面

---

## 9.5 设备信息查询

**SIP 方法：** MESSAGE（MANSCDP）—— 请求/响应成对
**方向：** 平台 → 设备（请求），设备 → 平台（响应）

### DeviceInfo 查询

```xml
<!-- 请求 -->
<Query>
  <CmdType>DeviceInfo</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
</Query>

<!-- 响应 -->
<Response>
  <CmdType>DeviceInfo</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
  <Result>OK</Result>
  <DeviceType>IPC</DeviceType>
  <Manufacturer>Hikvision</Manufacturer>
  <Model>DS-2CD2T85G1</Model>
  <Firmware>V5.7.0</Firmware>
  <MaxCamera>1</MaxCamera>
  <MaxAlarm>1</MaxAlarm>
</Response>
```

### DeviceStatus 查询

```xml
<!-- 请求 -->
<Query>
  <CmdType>DeviceStatus</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
</Query>

<!-- 响应 -->
<Response>
  <CmdType>DeviceStatus</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
  <Result>OK</Result>
  <Online>ONLINE</Online>
  <Status>OK</Status>
  <RecordStatus>RECORD</RecordStatus>  <!-- 或 UNRECORD -->
  <Alarmstatus num="1">...</Alarmstatus>
</Response>
```

### Catalog 目录查询

```xml
<!-- 请求 -->
<Query>
  <CmdType>Catalog</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
</Query>
```

响应分批返回——设备发送多条 Response MESSAGE，包含 `<SumNum>`（总数）和 `<DeviceList>` 列表。

### ConfigDownload 配置查询

```xml
<!-- 请求 -->
<Query>
  <CmdType>ConfigDownload</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
  <ConfigType>BasicParam</ConfigType>
  <!-- 可选值：VideoParamAttribute、OSDConfig、AudioParam 等 -->
</Query>
```

### 实现状态
- ✅ `CatalogQueryService.java` — 发送 Catalog 查询，处理分批响应
- ✅ `CatalogNotifyHandler.java` — 处理目录响应/通知
- ⬜ DeviceInfo / DeviceStatus 查询 — 未实现
- ⬜ ConfigDownload — 未实现（已列入 camera-config-ui 计划）

---

## 9.6 心跳（Keepalive）

**SIP 方法：** MESSAGE（MANSCDP）
**方向：** 设备 → 平台（强制，每 60 秒一次）

```xml
<Notify>
  <CmdType>Keepalive</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
  <Status>OK</Status>
</Notify>
```

平台回复 200 OK。若超过 3 个心跳周期未收到心跳，则判定设备离线。

### 实现状态
- ✅ `GbtSipListener.java` — 接收心跳，更新设备 `lastHeartbeatAt`

---

## 9.7 录像文件查询

**SIP 方法：** MESSAGE（MANSCDP）
**方向：** 平台 → 设备

```xml
<!-- 请求 -->
<Query>
  <CmdType>RecordInfo</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
  <StartTime>2022-06-01T00:00:00</StartTime>
  <EndTime>2022-06-01T23:59:59</EndTime>
  <FilePath/>
  <Address/>
  <Secrecy>0</Secrecy>
  <Type>all</Type>   <!-- all/time/alarm/manual -->
  <RecorderID/>
</Query>
```

响应与 Catalog 类似，分批返回多条 Response MESSAGE，包含 `<RecordList>`。

### 实现状态
- ✅ `RecordQueryService.java` + `RecordQueryRequest.java` — 已实现

---

## 9.8 历史视音频回放

**SIP 方法：** INVITE / ACK / BYE + INFO（会话内控制）
**方向：** 平台 → 设备

### 交互流程

```
平台                        设备
  |                           |
  |--- INVITE (s=Playback) -->|
  |<-- 200 OK (SDP answer) ---|
  |--- ACK ------------------>|
  |<======== RTP 视频流 ======|
  |--- INFO (MANSRTSP PLAY) ->|  （定位、暂停、继续、快进）
  |<-- 200 OK ----------------|
  |--- BYE ------------------>|
  |<-- 200 OK ----------------|
```

### 回放 SDP 关键字段

```
s=Playback
t=<开始时间戳> <结束时间戳>   <!-- 强制要求时间范围 -->
y=0200000001                   <!-- SSRC -->
```

### INFO 控制命令（MANSRTSP）

```
PLAY MANSRTSP/1.0\r\n
CSeq: 1\r\n
Scale: 1.0\r\n    <!-- 1.0=正常 2.0=2倍快进 -1.0=倒放 -->
Range: npt=<开始>-<结束>\r\n
```

支持命令：PLAY、PAUSE、TEARDOWN、SCALE（快进/倒放）

### 实现状态
- ⬜ 未实现

---

## 9.9 文件下载

**SIP 方法：** INVITE / ACK / BYE
**方向：** 平台 → 设备

与回放流程相同，但 SDP 中 `s=Download`。设备不限速，以最快速度传输。TEARDOWN 或 BYE 结束下载。

### 实现状态
- ⬜ 未实现

---

## 9.10 校时

**机制：** 嵌入在 REGISTER 200 OK 响应的 Date 头域
**方向：** 平台 → 设备（通过 Date 头域）

```
SIP/2.0 200 OK
Date: Sat, 01 Jun 2022 10:00:00 GMT
```

设备在注册成功后将本地时钟同步至 `Date` 头域所指示的时间。

### 实现状态
- ⬜ 当前 REGISTER 200 OK 响应中未设置 Date 头域

---

## 9.11 事件订阅/通知

**SIP 方法：** SUBSCRIBE / NOTIFY（遵循 RFC 6665）
**方向：** 平台 → 设备（SUBSCRIBE）；设备 → 平台（NOTIFY）

本节使用标准 RFC 6665 SUBSCRIBE/NOTIFY 对话（含 `Event:` 头域），与 §9.4 报警订阅（使用 SIP MESSAGE）不同。

### 支持的事件类型

| Event 头域值 | 说明 |
|-------------|------|
| Alarm | 报警事件通知 |
| MobilePosition | 移动设备 GPS 位置 |
| Catalog | 目录变更通知 |
| VideoAlert | 视频分析告警 |

### SUBSCRIBE 示例

```
SUBSCRIBE sip:<设备ID>@<域> SIP/2.0
Event: Alarm
Expires: 86400
Accept: Application/MANSCDP+xml
```

设备回复 200 OK。平台须在 Expires 前续订。

### NOTIFY 示例（报警事件）

```
NOTIFY sip:<平台ID>@<域> SIP/2.0
Event: Alarm
Subscription-State: active;expires=86400
Content-Type: Application/MANSCDP+xml

<Notify>
  <CmdType>Alarm</CmdType>
  ...
</Notify>
```

### 目录 NOTIFY

设备主动推送目录变更，消息体格式与目录查询 Response MESSAGE 相同。

### 实现状态
- ✅ `GbtSipListener.java` — 按 Event 头域分发入站 NOTIFY
- ✅ `CatalogSubscribeHandler.java` — 处理 Catalog NOTIFY
- ✅ `AlarmNotifyHandler.java` — 处理 Alarm NOTIFY（MESSAGE 和 NOTIFY 均支持）
- ⬜ 主动发送 SUBSCRIBE（RFC 6665）向上游订阅报警/位置/目录 — 未实现

---

## 9.12 语音广播与对讲

**SIP 方法：** MESSAGE（通知）+ INVITE / ACK / BYE（音频会话）
**方向：** 平台 → 设备

### 语音广播流程

```
平台                        设备
  |                           |
  |--- MESSAGE (Broadcast) -->|  （通知设备准备接听）
  |<-- 200 OK ----------------|
  |--- INVITE (仅音频) ------>|  （SDP: m=audio only）
  |<-- 200 OK ----------------|
  |--- ACK ------------------>|
  |<======== RTP 音频 ========|  （平台向设备扬声器播音）
  |--- BYE ------------------>|
```

### 广播通知 MESSAGE

```xml
<Notify>
  <CmdType>Broadcast</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
  <TargetID>34020000001310000001</TargetID>
</Notify>
```

### 双向对讲

INVITE 中 SDP 包含 `sendrecv` 属性，双方均可发送和接收音频。

### 实现状态
- ⬜ 未实现

---

## 9.13 软件升级

**SIP 方法：** MESSAGE（MANSCDP）
**方向：** 平台 → 设备

```xml
<Control>
  <CmdType>DeviceUpgrade</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
  <FirmwareID>V5.8.0</FirmwareID>
  <FirmwareAddr>http://192.168.1.100/firmware.bin</FirmwareAddr>
</Control>
```

设备从 `FirmwareAddr` 下载固件并应用，完成后重启并重新注册。

### 实现状态
- ⬜ 未实现

---

## 9.14 图像抓拍

**SIP 方法：** MESSAGE（MANSCDP）
**方向：** 平台 → 设备

```xml
<Control>
  <CmdType>SnapShotConfig</CmdType>
  <SN>1</SN>
  <DeviceID>34020000001310000001</DeviceID>
  <SnapNum>1</SnapNum>           <!-- 抓拍张数 -->
  <Interval>0</Interval>         <!-- 抓拍间隔（秒） -->
  <UploadAddr>http://192.168.1.100/upload</UploadAddr>
  <Resolution>HD720P</Resolution>
</Control>
```

设备抓拍图像后通过 HTTP POST 上传至 `UploadAddr`。

### 实现状态
- ⬜ 未实现

---

## 实现状态汇总

| 章节 | 功能 | 状态 |
|------|------|------|
| 9.1 | 设备注册（服务端，接收下级） | ✅ |
| 9.1 | 设备注册（客户端，向上级级联） | ✅ |
| 9.2 | 实时视频 INVITE/RTP | ✅ |
| 9.3 | PTZ 控制（MESSAGE/PTZCmd） | ✅ |
| 9.3 | 设备控制（DeviceControl） | ⬜ |
| 9.4 | 接收报警通知 | ✅ |
| 9.4 | 报警订阅（MESSAGE/Alarm） | ✅ |
| 9.5 | Catalog 目录查询 | ✅ |
| 9.5 | DeviceInfo / DeviceStatus 查询 | ⬜ |
| 9.5 | ConfigDownload 配置查询 | ⬜ |
| 9.6 | 接收心跳 | ✅ |
| 9.7 | 录像文件查询 | ✅ |
| 9.8 | 历史视音频回放 | ⬜ |
| 9.9 | 文件下载 | ⬜ |
| 9.10 | 校时（Date 头域） | ⬜ |
| 9.11 | 接收 Catalog NOTIFY | ✅ |
| 9.11 | 接收 Alarm NOTIFY | ✅ |
| 9.11 | 主动发送 SUBSCRIBE（向上游） | ⬜ |
| 9.12 | 语音广播/对讲 | ⬜ |
| 9.13 | 软件升级 | ⬜ |
| 9.14 | 图像抓拍 | ⬜ |

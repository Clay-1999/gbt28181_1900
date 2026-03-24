## Source: playback-session

## ADDED Requirements

### Requirement: 北向 REST 发起历史回放
系统 SHALL 提供 REST 接口，通过 SIP INVITE(s=Playback) 向设备请求历史视频流，返回 ZLM 可播放的流 URL。

请求体：
- `startTime`（字符串，必填）：回放开始时间，格式 `YYYY-MM-DDTHH:MM:SS`
- `endTime`（字符串，必填）：回放结束时间，格式 `YYYY-MM-DDTHH:MM:SS`

INVITE SDP 关键字段：
- `s=Playback`
- `t=<开始时间 Unix 时间戳> <结束时间 Unix 时间戳>`
- `y=1xxxxxxxxx`（SSRC，首位为 1 标识回放）

#### Scenario: 成功发起外域设备回放
- **WHEN** 调用 `POST /api/devices/remote/{deviceId}/playback/start`，body 含合法 startTime/endTime
- **THEN** 系统发送 INVITE(s=Playback)，设备返回 200 OK 后，接口返回 `{"streamUrl": "rtsp://..."}`

#### Scenario: 成功发起本端设备回放
- **WHEN** 调用 `POST /api/devices/local/{gbDeviceId}/playback/start`，body 含合法 startTime/endTime
- **THEN** 系统通过 IVS1900 互联配置发送 INVITE(s=Playback)，返回流 URL

#### Scenario: 设备不存在返回 404
- **WHEN** deviceId 不存在于数据库
- **THEN** 返回 `404 Not Found`

#### Scenario: 设备无响应返回 504
- **WHEN** 发送 INVITE 后 10 秒内设备未返回 200 OK
- **THEN** 返回 `504 Gateway Timeout`，body 含 `{"error": "..."}`

---

### Requirement: 北向 REST 终止历史回放
系统 SHALL 提供 REST 接口，通过发送 SIP BYE 终止指定设备的回放会话。

#### Scenario: 终止正在进行的回放
- **WHEN** 调用 `POST /api/devices/{type}/{deviceId}/playback/stop`，该设备存在活跃回放会话
- **THEN** 发送 BYE，ZLM 停止接收该 SSRC 的 RTP 流，返回 `200 OK`

#### Scenario: 无活跃回放时调用 stop
- **WHEN** 调用 stop 但该设备无活跃回放会话
- **THEN** 返回 `200 OK`（幂等）

---

### Requirement: 北向 REST 控制回放进度
系统 SHALL 提供 REST 接口，通过 SIP INFO（MANSRTSP）控制回放（暂停、继续、快进、定位）。

INFO 体格式（Content-Type: Application/MANSRTSP）：
```
PLAY MANSRTSP/1.0
CSeq: {n}
Scale: {scale}        （1.0=正常, 2.0=2倍快进, -1.0=倒放）
Range: npt={start}-{end}
```

请求体字段：
- `action`（字符串，必填）：`play`、`pause`、`scale`、`seek`
- `scale`（浮点，可选）：仅 action=scale 时使用，如 `2.0`
- `seekTime`（字符串，可选）：仅 action=seek 时使用，格式 `YYYY-MM-DDTHH:MM:SS`

#### Scenario: 暂停回放
- **WHEN** 调用 `POST /api/devices/{type}/{deviceId}/playback/control`，body `{"action": "pause"}`
- **THEN** 发送 INFO(PAUSE MANSRTSP/1.0)，返回 `{"sent": true}`

#### Scenario: 快进回放
- **WHEN** 调用 control 接口，body `{"action": "scale", "scale": 2.0}`
- **THEN** 发送 INFO(PLAY MANSRTSP/1.0 Scale: 2.0)，返回 `{"sent": true}`

#### Scenario: 定位回放
- **WHEN** 调用 control 接口，body `{"action": "seek", "seekTime": "2022-06-01T10:30:00"}`
- **THEN** 发送 INFO(PLAY MANSRTSP/1.0 Range: npt=...)，返回 `{"sent": true}`

#### Scenario: 无活跃回放时调用 control
- **WHEN** 无活跃回放会话
- **THEN** 返回 `404 Not Found`

---

### Requirement: 回放会话状态管理
系统 SHALL 维护每台设备的回放会话状态（in-memory），包括 callId、deviceId、startTime、endTime、ZLM 流信息。

#### Scenario: 开始新回放时若已有活跃会话则先终止
- **WHEN** 调用 start 时该设备已有活跃回放会话
- **THEN** 先发送 BYE 终止旧会话，再建立新会话

#### Scenario: BYE 触发会话清理
- **WHEN** 设备主动发送 BYE 结束回放
- **THEN** PlaybackSessionStore 移除该 callId 对应的会话记录

---

## Source: record-query

## ADDED Requirements

### Requirement: 录像查询接口
系统 SHALL 提供 REST 接口，接受设备类型、设备 ID 和时间范围，通过 SIP MESSAGE 向目标设备发送 RecordInfo 查询，等待响应并返回录像条目列表。

接口定义：
- `POST /api/devices/local/{gbDeviceId}/records/query`
- `POST /api/devices/remote/{deviceId}/records/query`

请求体：
```json
{
  "startTime": "2026-03-01T00:00:00",
  "endTime":   "2026-03-20T23:59:59",
  "type":      "all"
}
```

响应体（成功）：
```json
{
  "sumNum": 5,
  "items": [
    {
      "deviceId": "34020000001310000002",
      "name": "192.168.10.6",
      "startTime": "2026-03-10T08:00:00",
      "endTime":   "2026-03-10T09:00:00",
      "type": "time",
      "secrecy": 0,
      "filePath": "/record/xxx.mp4"
    }
  ]
}
```

响应体（超时）：HTTP 504

#### Scenario: 成功查询到录像列表
- **WHEN** 客户端 POST 有效时间范围到本端设备录像查询接口
- **THEN** 系统发送 SIP RecordInfo MESSAGE 到目标设备，等待响应后返回 200 和录像列表 JSON

#### Scenario: 设备不响应超时
- **WHEN** 客户端 POST 录像查询请求，10秒内设备无响应
- **THEN** 系统返回 HTTP 504

#### Scenario: 外域设备录像查询
- **WHEN** 客户端 POST 有效时间范围到外域设备录像查询接口
- **THEN** 系统通过该设备所属互联配置的 SIP 连接发送 RecordInfo 查询

### Requirement: SIP RecordInfo 请求报文
系统 SHALL 构建符合 GB/T 28181 A.2.4.5 的 RecordInfo 查询 XML：
```xml
<?xml version="1.0" encoding="GB2312"?>
<Query>
  <CmdType>RecordInfo</CmdType>
  <SN>{sn}</SN>
  <DeviceID>{deviceId}</DeviceID>
  <StartTime>{startTime}</StartTime>
  <EndTime>{endTime}</EndTime>
  <Type>{type}</Type>
</Query>
```
时间格式为 `yyyy-MM-dd'T'HH:mm:ss`。

#### Scenario: 构建标准 RecordInfo XML
- **WHEN** 系统需要发送录像查询时
- **THEN** 生成的 XML 包含 CmdType=RecordInfo、SN、DeviceID、StartTime、EndTime 字段

### Requirement: SIP RecordInfo 响应路由
系统 SHALL 识别入站 SIP MESSAGE 中 `<CmdType>RecordInfo</CmdType>` 的响应报文，通过 SN 匹配完成对应的 CompletableFuture。

#### Scenario: 响应路由到正确的 future
- **WHEN** 收到 CmdType=RecordInfo 且根节点为 Response 的 SIP MESSAGE
- **THEN** 从 XML 中提取 SN，找到对应 pending future 并 complete

#### Scenario: 无匹配的 SN
- **WHEN** 收到 RecordInfo 响应但 SN 无对应 pending future
- **THEN** 正常回复 200 OK 并忽略

---

## Source: recording-management-ui

## ADDED Requirements

### Requirement: 独立录像管理页面
系统 SHALL 提供独立路由页面 `/recordings`（`RecordingView.vue`），顶部导航中显示"录像管理"入口，用户可直接访问。

#### Scenario: 从导航进入录像管理
- **WHEN** 用户点击顶部导航"录像管理"
- **THEN** 路由跳转至 `/recordings`，显示录像管理页面

#### Scenario: 直接访问 URL
- **WHEN** 用户在浏览器地址栏输入 `/recordings`
- **THEN** 显示录像管理页面，不跳转到其他页面

### Requirement: 设备选择与时间范围查询
系统 SHALL 在录像管理页面提供设备选择下拉框（包含本端和外域设备分组）、开始时间和结束时间选择器，以及"查询"按钮。点击查询后，调用 `POST /api/devices/{type}/{id}/records/query` 获取录像列表。

#### Scenario: 查询到录像列表
- **WHEN** 用户选择设备、设置时间范围后点击"查询"
- **THEN** 接口返回 200，页面展示录像条目表格（录像名称、开始时间、结束时间、类型、操作）

#### Scenario: 设备无录像返回空列表
- **WHEN** 查询结果 sumNum=0
- **THEN** 表格显示"暂无录像"空状态

#### Scenario: 设备响应超时
- **WHEN** 接口返回 504
- **THEN** 页面显示超时警告提示，不显示表格

#### Scenario: 未选设备直接查询
- **WHEN** 用户未选择设备点击"查询"
- **THEN** 提示"请先选择相机"，不发起请求

### Requirement: 录像播放
系统 SHALL 在录像列表每行提供"播放"按钮，点击后调用 `POST /api/devices/{type}/{id}/playback/start`（携带 startTime/endTime），获取 streamUrl 后在页面内嵌播放器播放 HTTP-FLV 流。同时提供"停止"按钮，调用 `POST /api/devices/{type}/{id}/playback/stop`。

#### Scenario: 点击播放录像
- **WHEN** 用户点击录像条目的"播放"按钮
- **THEN** 调用 playback/start，成功后播放器显示并开始播放视频流

#### Scenario: 停止回放
- **WHEN** 用户点击"停止"按钮
- **THEN** 调用 playback/stop，播放器关闭

#### Scenario: 回放启动超时
- **WHEN** playback/start 接口返回 504
- **THEN** 提示"回放启动超时"，播放器不显示

### Requirement: 设备列表页移除录像 tab
系统 SHALL 从 `DevicesView.vue` 的"设备列表"页面移除"录像查询" tab，以及相关的 data 状态和 methods（`queryRecords`、`startPlayback`、`stopPlayback` 等）。

#### Scenario: 设备列表页不再显示录像查询 tab
- **WHEN** 用户访问 `/devices` 设备列表页
- **THEN** 页面只显示"本端设备"和"外域设备"两个 tab，无"录像查询" tab

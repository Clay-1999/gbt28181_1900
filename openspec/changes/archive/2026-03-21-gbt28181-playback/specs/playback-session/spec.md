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

## ADDED Requirements

### Requirement: 发起语音广播（sendonly）

系统 SHALL 提供 REST 接口，接受设备类型和设备 ID，向目标设备发送 GB/T 28181 §9.11 语音广播 SIP INVITE，建立单向音频会话（平台 → 设备）。

接口定义：
- `POST /api/devices/local/{gbDeviceId}/audio/broadcast/start`
- `POST /api/devices/remote/{deviceId}/audio/broadcast/start`

成功响应：HTTP 200，body 为空

SDP 特征：
```
s=Broadcast
m=audio <zlmPort> RTP/AVP 0 8
a=rtpmap:0 PCMU/8000
a=rtpmap:8 PCMA/8000
a=sendonly
y=<ssrc>
```

#### Scenario: 成功发起广播（本端设备）

- **WHEN** 客户端 POST 到本端设备广播启动接口，设备在线且 ZLM 可用
- **THEN** 系统向 ZLM 申请 RTP 端口，构造 s=Broadcast sendonly SDP，发送 SIP INVITE，收到 200 OK 后发送 ACK，存储 AudioSession，返回 HTTP 200

#### Scenario: 成功发起广播（外域设备）

- **WHEN** 客户端 POST 到外域设备广播启动接口，设备在线且 ZLM 可用
- **THEN** 系统通过该设备所属互联配置的 SIP 连接发送 INVITE，流程同本端设备

#### Scenario: 广播已在运行时重新发起

- **WHEN** 该设备已有活跃语音广播会话，客户端再次 POST 广播启动
- **THEN** 系统先发送 BYE 停止旧会话，再发起新的 INVITE，返回 HTTP 200

#### Scenario: 设备不存在

- **WHEN** 客户端 POST 广播启动，deviceId 在本端设备表或外域设备表中不存在
- **THEN** 系统返回 HTTP 404

#### Scenario: 设备无响应（INVITE 超时）

- **WHEN** 客户端 POST 广播启动，SIP INVITE 发出后 10 秒内设备无 200 OK 响应
- **THEN** 系统释放已申请的 ZLM RTP 端口，返回 HTTP 504

---

### Requirement: 发起语音对讲（sendrecv）

系统 SHALL 提供 REST 接口，接受设备类型和设备 ID，向目标设备发送 GB/T 28181 §9.12 语音对讲 SIP INVITE，建立双向音频会话（平台 ↔ 设备）。

接口定义：
- `POST /api/devices/local/{gbDeviceId}/audio/talk/start`
- `POST /api/devices/remote/{deviceId}/audio/talk/start`

成功响应：HTTP 200，body 为空

SDP 特征：
```
s=Talk
m=audio <zlmPort> RTP/AVP 0 8
a=rtpmap:0 PCMU/8000
a=rtpmap:8 PCMA/8000
a=sendrecv
y=<ssrc>
```

#### Scenario: 成功发起对讲

- **WHEN** 客户端 POST 到对讲启动接口，设备在线且 ZLM 可用
- **THEN** 系统向 ZLM 申请 RTP 端口，构造 s=Talk sendrecv SDP，发送 SIP INVITE，收到 200 OK 后解析设备 RTP 端口和 IP，发送 ACK，存储 AudioSession（含 deviceRtpIp/deviceRtpPort），返回 HTTP 200

#### Scenario: 对讲 200 OK SDP 解析设备 RTP 端口

- **WHEN** 收到设备对讲 INVITE 200 OK，SDP 中含 `m=audio <devicePort> ...`
- **THEN** 系统解析设备 RTP 端口和 c= 行 IP，存入 AudioSession.deviceRtpPort / deviceRtpIp

#### Scenario: 设备不存在

- **WHEN** 客户端 POST 对讲启动，deviceId 不存在
- **THEN** 系统返回 HTTP 404

#### Scenario: 设备无响应（INVITE 超时）

- **WHEN** SIP INVITE 发出后 10 秒内设备无 200 OK
- **THEN** 系统释放 ZLM RTP 端口，返回 HTTP 504

---

### Requirement: 停止音频会话（BYE，幂等）

系统 SHALL 提供 REST 接口，停止指定设备的语音广播或语音对讲会话。接口为幂等操作——若该设备无活跃音频会话，返回 HTTP 200 而非错误。

接口定义：
- `POST /api/devices/local/{gbDeviceId}/audio/broadcast/stop`
- `POST /api/devices/remote/{deviceId}/audio/broadcast/stop`
- `POST /api/devices/local/{gbDeviceId}/audio/talk/stop`
- `POST /api/devices/remote/{deviceId}/audio/talk/stop`

成功响应：HTTP 200，body 为空

#### Scenario: 成功停止活跃广播会话

- **WHEN** 客户端 POST 广播停止接口，该设备有活跃 AudioSession（mode=broadcast）
- **THEN** 系统发送 SIP BYE，释放 ZLM RTP 端口，从 AudioSessionStore 移除会话，返回 HTTP 200

#### Scenario: 成功停止活跃对讲会话

- **WHEN** 客户端 POST 对讲停止接口，该设备有活跃 AudioSession（mode=talk）
- **THEN** 系统发送 SIP BYE，释放 ZLM RTP 端口，从 AudioSessionStore 移除会话，返回 HTTP 200

#### Scenario: 无活跃会话时停止（幂等）

- **WHEN** 客户端 POST 停止接口，该设备无活跃音频会话
- **THEN** 系统直接返回 HTTP 200，不发送 BYE，不报错

#### Scenario: 设备主动发送 BYE

- **WHEN** 设备主动发来 SIP BYE，Call-ID 匹配某活跃 AudioSession
- **THEN** 系统释放 ZLM RTP 端口，从 AudioSessionStore 移除会话

---

### Requirement: 音频会话状态管理（AudioSessionStore）

系统 SHALL 使用 AudioSessionStore 管理音频会话状态，确保并发安全，支持按 deviceId 和 callId 两种方式查找会话。

#### Scenario: 同一设备同时只有一个音频会话

- **WHEN** AudioSessionStore 中已有 deviceId 对应的 AudioSession
- **THEN** put 操作覆盖旧会话（调用方应先 stop 旧会话再 put 新会话）

#### Scenario: 按 callId 查找会话

- **WHEN** 收到 BYE 或 200 OK 仅有 Call-ID 信息
- **THEN** AudioSessionStore.findByCallId(callId) 返回对应的 AudioSession（若存在）

#### Scenario: INVITE 200 OK 路由优先级

- **WHEN** GbtSipListener 收到 INVITE 200 OK
- **THEN** 先检查 SipAudioService 是否有对应 callId 的 pending future；有则交给 SipAudioService 处理；否则按原有顺序继续（回放 → 视频流）

## ADDED Requirements

### Requirement: Start stream via SIP INVITE
系统 SHALL 通过 GB/T 28181 SIP INVITE 流程向外域平台发起实时视频流请求。

#### Scenario: Successful stream start
- **WHEN** 调用 `POST /api/devices/remote/{deviceId}/stream/start`
- **THEN** 系统向外域平台发送 SIP INVITE，SDP offer 包含 `y=` SSRC 字段和 ZLMediaKit RTP 接收地址
- **THEN** 收到 200 OK 后发送 ACK，完成三次握手
- **THEN** 接口返回 HTTP-FLV 播放地址（由 ZLMediaKit 提供）

#### Scenario: Device not found
- **WHEN** 调用 `POST /api/devices/remote/{deviceId}/stream/start`，deviceId 不存在
- **THEN** 接口返回 404

#### Scenario: INVITE timeout
- **WHEN** 发送 INVITE 后 10 秒内未收到 200 OK
- **THEN** 接口返回 503，body 包含 `{"error": "INVITE timeout"}`

#### Scenario: Duplicate stream request
- **WHEN** 同一 deviceId 已有活跃流会话，再次调用 start
- **THEN** 系统先发送 BYE 终止旧会话，再发起新 INVITE

### Requirement: Stop stream via SIP BYE
系统 SHALL 通过 SIP BYE 终止视频流会话并释放 ZLMediaKit 资源。

#### Scenario: Successful stream stop
- **WHEN** 调用 `POST /api/devices/remote/{deviceId}/stream/stop`
- **THEN** 系统发送 SIP BYE，从 `StreamSessionStore` 移除会话，调用 ZLMediaKit 关闭 RTP 端口
- **THEN** 接口返回 200 OK

#### Scenario: No active session
- **WHEN** 调用 stop 但该 deviceId 无活跃会话
- **THEN** 接口返回 404，body 包含 `{"error": "No active stream session"}`

### Requirement: Stream session lifecycle management
系统 SHALL 维护外域设备流会话的完整生命周期状态。

#### Scenario: Session stored after INVITE success
- **WHEN** INVITE 200 OK 收到并 ACK 发送完成
- **THEN** `StreamSessionStore` 存储 Call-ID、From-tag、To-tag、CSeq、ZLM stream key

#### Scenario: Session cleaned up after BYE
- **WHEN** BYE 发送完成或收到对端 BYE
- **THEN** `StreamSessionStore` 移除对应 deviceId 的会话记录

### Requirement: Handle incoming BYE from remote
系统 SHALL 处理外域平台主动发送的 SIP BYE 请求。

#### Scenario: Remote sends BYE
- **WHEN** 收到外域平台发来的 SIP BYE
- **THEN** 系统回复 200 OK，清理 `StreamSessionStore` 中对应会话，关闭 ZLMediaKit RTP 端口

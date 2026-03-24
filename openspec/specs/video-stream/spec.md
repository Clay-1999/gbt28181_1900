## Source: local-device-stream

## ADDED Requirements

### Requirement: 本端设备视频流启动
系统 SHALL 支持通过 REST API 向本端 IVS1900 相机发起 GB/T 28181 SIP INVITE，建立实时视频流会话，返回 HTTP-FLV 播放地址。

#### Scenario: 成功启动视频流
- **WHEN** 客户端发送 `POST /api/devices/local/{gbDeviceId}/stream/start`，且设备已注册、ZLMediaKit 可用
- **THEN** 系统向 IVS1900 发送 SIP INVITE，收到 200 OK 后发送 ACK，返回 HTTP 200 及 `{"streamUrl": "<flv-url>"}`

#### Scenario: 设备未注册
- **WHEN** 客户端发送 `POST /api/devices/local/{gbDeviceId}/stream/start`，但 IVS1900 未在平台注册
- **THEN** 返回 HTTP 404，body 包含错误描述

#### Scenario: INVITE 超时
- **WHEN** IVS1900 在 10 秒内未回复 200 OK
- **THEN** 返回 HTTP 504，ZLMediaKit RTP 端口已释放

#### Scenario: 已有活跃会话时重新启动
- **WHEN** 该 gbDeviceId 已有活跃流会话，客户端再次调用 stream/start
- **THEN** 系统先停止旧会话（发送 BYE），再建立新会话

### Requirement: 本端设备视频流停止
系统 SHALL 支持通过 REST API 停止本端 IVS1900 相机的视频流会话，发送 SIP BYE 并释放 ZLMediaKit 资源。

#### Scenario: 成功停止视频流
- **WHEN** 客户端发送 `POST /api/devices/local/{gbDeviceId}/stream/stop`，且存在活跃会话
- **THEN** 系统发送 SIP BYE，关闭 ZLMediaKit RTP 端口，返回 HTTP 200

#### Scenario: 无活跃会话时停止
- **WHEN** 客户端发送 `POST /api/devices/local/{gbDeviceId}/stream/stop`，但无活跃会话
- **THEN** 返回 HTTP 200（幂等，忽略）

### Requirement: 本端流 SIP INVITE 目标地址
系统 SHALL 使用 IVS1900 的注册地址（IP、端口）作为 SIP INVITE 的目标，使用 `ivsCameraId` 作为 Request-URI 中的用户部分。

#### Scenario: 构造 INVITE 目标
- **WHEN** 系统发起本端设备 INVITE
- **THEN** Request-URI 为 `sip:{ivsCameraId}@{registeredIp}:{registeredPort}`，SDP 中 SSRC 首位为 0

---

## Source: remote-device-stream

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

---

## Source: northbound-local-stream

## ADDED Requirements

### Requirement: 响应上级 INVITE 拉取本端相机流
系统 SHALL 接受上级平台发来的 SIP INVITE 请求，当目标 DeviceID 为本端 IVS1900 相机（存在于 `ivs1900_camera_mapping`）时，通过 ZLMediaKit 拉起该相机流并返回 SDP。

#### Scenario: 上级 INVITE 本端相机
- **WHEN** 收到上级 SIP INVITE，Request-URI 的 user 部分为本端相机的 `gbDeviceId`
- **THEN** 回复 `100 Trying`，通过 ZLMediaKit 分配 RTP 端口并拉起 IVS1900 流，回复 `200 OK` 并携带 SDP（含 RTP 端口、编码格式）

#### Scenario: 本端相机不存在
- **WHEN** 收到上级 INVITE，目标 gbDeviceId 在 `ivs1900_camera_mapping` 中不存在
- **THEN** 回复 `404 Not Found`

#### Scenario: 流启动超时
- **WHEN** ZLMediaKit 拉流超时（超过 10 秒）
- **THEN** 回复 `504 Server Time-out`，释放已分配的 RTP 端口

#### Scenario: 上级发送 BYE 停止播放
- **WHEN** 上级发送 BYE 结束会话
- **THEN** 回复 `200 OK`，停止 ZLMediaKit 流，释放 RTP 端口

### Requirement: 本端相机流 SDP 格式
系统返回的 SDP SHALL 符合 GB/T 28181 要求，包含 RTP 媒体描述。

#### Scenario: SDP 包含必要字段
- **WHEN** 成功拉起本端相机流
- **THEN** 200 OK SDP 包含 `m=video <rtp_port> RTP/AVP 96`、`a=rtpmap:96 PS/90000`（或 H.264）、`c=IN IP4 <本机IP>`

---

## Source: zlmediakit-integration

## ADDED Requirements

### Requirement: Open RTP receive port
系统 SHALL 通过 ZLMediaKit HTTP API 创建 RTP 收流端口，用于接收外域设备推送的 RTP 流。

#### Scenario: Open port success
- **WHEN** 调用 ZLMediaKit `POST /index/api/openRtpServer`，参数包含 stream_id 和 port=0（自动分配）
- **THEN** ZLMediaKit 返回分配的端口号
- **THEN** 该端口号用于填写 SDP offer 中的媒体端口

#### Scenario: ZLMediaKit unavailable
- **WHEN** ZLMediaKit 服务不可达
- **THEN** 抛出异常，stream start 接口返回 503

### Requirement: Get HTTP-FLV play URL
系统 SHALL 在 RTP 流建立后，返回 ZLMediaKit 提供的 HTTP-FLV 播放地址。

#### Scenario: FLV URL construction
- **WHEN** INVITE 成功，RTP 流开始推送
- **THEN** 系统构造 HTTP-FLV URL 格式：`http://{zlm-host}:{zlm-http-port}/live/{stream_id}.flv`
- **THEN** 该 URL 作为 start 接口的响应返回给前端

### Requirement: Close RTP port on stream stop
系统 SHALL 在流会话结束时关闭 ZLMediaKit 的 RTP 收流端口。

#### Scenario: Close port on BYE
- **WHEN** 流会话通过 BYE 终止（主动或被动）
- **THEN** 调用 ZLMediaKit `POST /index/api/closeRtpServer`，传入对应 stream_id

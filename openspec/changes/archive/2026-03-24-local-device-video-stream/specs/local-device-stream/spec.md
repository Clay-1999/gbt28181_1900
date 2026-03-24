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

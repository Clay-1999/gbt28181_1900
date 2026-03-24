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

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

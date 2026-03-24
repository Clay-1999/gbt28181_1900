## ADDED Requirements

### Requirement: 按 DeviceID 归属路由 SIP MESSAGE 命令
系统 SHALL 在收到 SIP MESSAGE（CmdType 为 ConfigDownload 或 DeviceConfig）时，解析目标 DeviceID，按以下优先级路由：
1. DeviceID 存在于 `ivs1900_camera_mapping` → 转交本端处理器（调 ivs1900 REST API）
2. DeviceID 存在于 `remote_device` → 转交外域透传处理器（转发给对端平台）
3. 两者均无 → 回复 `SIP/2.0 404 Not Found`

#### Scenario: DeviceID 属于本端 IVS1900 相机
- **WHEN** 收到 ConfigDownload/DeviceConfig SIP MESSAGE，DeviceID 在 `ivs1900_camera_mapping`
- **THEN** 路由至本端处理器，调用 ivs1900 REST API，结果回复对端

#### Scenario: DeviceID 属于外域平台设备
- **WHEN** 收到 ConfigDownload/DeviceConfig SIP MESSAGE，DeviceID 在 `remote_device`
- **THEN** 路由至透传处理器，将原始 MESSAGE 转发至 `remote_device.interconnect_config_id` 对应的对端平台

#### Scenario: DeviceID 未知
- **WHEN** 收到 ConfigDownload/DeviceConfig SIP MESSAGE，DeviceID 既不在 `ivs1900_camera_mapping` 也不在 `remote_device`
- **THEN** 回复 `SIP/2.0 404 Not Found`，Body 为空

### Requirement: 外域设备命令透传
系统 SHALL 将外域设备命令原样转发至对端平台，并透传对端应答。

#### Scenario: 透传成功并收到对端应答
- **WHEN** 外域设备命令转发后，对端平台在 5 秒内回复 SIP 200 OK（含或不含应答 MESSAGE）
- **THEN** 将对端应答透传回原始请求方

#### Scenario: 对端无响应超时
- **WHEN** 外域设备命令转发后，5 秒内未收到对端响应
- **THEN** 回复原始请求方 `SIP/2.0 408 Request Timeout`

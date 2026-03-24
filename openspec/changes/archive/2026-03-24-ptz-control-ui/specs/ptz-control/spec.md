## ADDED Requirements

### Requirement: 云台方向控制接口
系统 SHALL 提供 REST 接口，接收云台控制动作并通过 GB/T 28181 `DeviceControl` SIP MESSAGE 发送 `PTZCmd`。

#### Scenario: 发送方向控制命令
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/control`，body 为 `{ "action": "left", "speed": 5 }`
- **THEN** 系统构建 `PTZCmd` 字节串，发送 DeviceControl SIP MESSAGE，返回 `{"success": true}`

#### Scenario: 发送停止命令
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/control`，body 为 `{ "action": "stop" }`
- **THEN** 系统发送 PTZCmd 停止帧，返回 `{"success": true}`

#### Scenario: 变倍控制
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/control`，body 为 `{ "action": "zoom_in", "speed": 3 }`
- **THEN** 系统发送对应变倍 PTZCmd，返回 `{"success": true}`

#### Scenario: 设备不存在
- **WHEN** 客户端传入不存在的 deviceId
- **THEN** 系统返回 HTTP 404

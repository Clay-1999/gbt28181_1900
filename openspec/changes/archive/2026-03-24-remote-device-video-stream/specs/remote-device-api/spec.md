## ADDED Requirements

### Requirement: Stream start endpoint
系统 SHALL 提供 `POST /api/devices/remote/{deviceId}/stream/start` 接口。

#### Scenario: Successful response
- **WHEN** 流建立成功
- **THEN** 返回 HTTP 200，body：`{"streamUrl": "http://...", "deviceId": "..."}`

#### Scenario: Error response
- **WHEN** 流建立失败（设备不存在、超时、ZLM 不可用）
- **THEN** 返回对应 HTTP 错误码（404/503），body：`{"error": "<message>"}`

### Requirement: Stream stop endpoint
系统 SHALL 提供 `POST /api/devices/remote/{deviceId}/stream/stop` 接口。

#### Scenario: Successful stop
- **WHEN** 会话存在且 BYE 发送成功
- **THEN** 返回 HTTP 200，body：`{"success": true}`

#### Scenario: No session
- **WHEN** 该 deviceId 无活跃会话
- **THEN** 返回 HTTP 404，body：`{"error": "No active stream session"}`

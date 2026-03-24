## Source: remote-device-store

## ADDED Requirements

### Requirement: 外域设备持久化
系统 SHALL 维护 `remote_device` 表，存储从外域平台同步来的设备信息，每条记录关联到一个互联配置。

#### Scenario: 新设备入库
- **WHEN** 收到外域设备数据（deviceId 不存在于表中）
- **THEN** 插入新记录，包含 `deviceId`、`name`、`status`、`interconnectConfigId`、`syncedAt`

#### Scenario: 已有设备更新
- **WHEN** 收到外域设备数据（deviceId 已存在于表中）
- **THEN** 更新 `name`、`status`、`syncedAt`，`deviceId` 和 `interconnectConfigId` 不变

#### Scenario: 按互联配置查询
- **WHEN** 查询指定 `interconnectConfigId` 的外域设备
- **THEN** 返回该互联配置下所有设备记录

---

## Source: remote-device-api

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

---

## Note: remote-device-stream skipped (already included in video-stream group)

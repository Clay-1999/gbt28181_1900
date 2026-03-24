## ADDED Requirements

### Requirement: 主动查询设备信息（DeviceInfo）
系统 SHALL 支持向本端（IVS1900）或外域设备发送 `<Query><CmdType>DeviceInfo</CmdType>` SIP MESSAGE，等待设备响应（10s 超时），并通过北向 REST 接口返回查询结果。

#### Scenario: 查询本端设备信息成功
- **WHEN** 调用 `GET /api/devices/local/{gbDeviceId}/info`
- **THEN** 系统向该设备发送 DeviceInfo MESSAGE，等待响应，返回包含 `deviceName`、`manufacturer`、`model`、`firmware` 等字段的 JSON

#### Scenario: 查询外域设备信息成功
- **WHEN** 调用 `GET /api/devices/remote/{deviceId}/info`
- **THEN** 系统向该外域设备发送 DeviceInfo MESSAGE，等待响应，返回设备信息 JSON

#### Scenario: 查询超时
- **WHEN** 设备 10 秒内未响应
- **THEN** REST 接口返回 `504 Gateway Timeout`，body 包含 `{"error": "设备响应超时"}`

#### Scenario: 设备不存在
- **WHEN** 指定的 gbDeviceId 或 deviceId 不存在
- **THEN** REST 接口返回 `404 Not Found`

---

### Requirement: 主动查询设备状态（DeviceStatus）
系统 SHALL 支持向本端或外域设备发送 `<Query><CmdType>DeviceStatus</CmdType>` SIP MESSAGE，等待响应（10s 超时），通过北向 REST 接口返回状态信息。

#### Scenario: 查询本端设备状态成功
- **WHEN** 调用 `GET /api/devices/local/{gbDeviceId}/status`
- **THEN** 系统向该设备发送 DeviceStatus MESSAGE，等待响应，返回包含 `online`、`recordStatus`、`alarmStatus` 等字段的 JSON

#### Scenario: 查询外域设备状态成功
- **WHEN** 调用 `GET /api/devices/remote/{deviceId}/status`
- **THEN** 系统向该外域设备发送 DeviceStatus MESSAGE，等待响应，返回状态 JSON

#### Scenario: 查询超时
- **WHEN** 设备 10 秒内未响应
- **THEN** REST 接口返回 `504 Gateway Timeout`

---

### Requirement: 接收并路由 DeviceInfo/DeviceStatus 响应
系统 SHALL 在收到设备上报的 `<Response><CmdType>DeviceInfo</CmdType>` 或 `<Response><CmdType>DeviceStatus</CmdType>` MESSAGE 时，将响应内容匹配到对应的等待 future 并 complete。

#### Scenario: 响应匹配成功
- **WHEN** 收到 CmdType=DeviceInfo 或 DeviceStatus 的 Response MESSAGE，SN 与 pending 中的 key 匹配
- **THEN** 对应 CompletableFuture 被 complete，REST 接口返回解析后的 JSON

#### Scenario: 响应无匹配（孤立响应）
- **WHEN** 收到 Response MESSAGE 但 SN 无对应 pending future（已超时或未发起查询）
- **THEN** 忽略该响应，记录 debug 日志

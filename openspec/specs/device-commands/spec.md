## Source: device-control-commands

## Requirements

### Requirement: 北向 REST 触发布/撤防（GuardCmd）
系统 SHALL 提供 REST 接口，向本端或外域设备发送 `<Control><CmdType>GuardCmd</CmdType>` SIP MESSAGE，用于布防（SetGuard）或撤防（ResetGuard）。

#### Scenario: 向本端设备发送布防命令
- **WHEN** 调用 `POST /api/devices/local/{gbDeviceId}/control/guard`，body `{"cmd": "SetGuard"}`
- **THEN** 系统向该设备发送 GuardCmd MESSAGE，返回 `{"sent": true}`

#### Scenario: 向外域设备发送撤防命令
- **WHEN** 调用 `POST /api/devices/remote/{deviceId}/control/guard`，body `{"cmd": "ResetGuard"}`
- **THEN** 系统向该设备发送 GuardCmd MESSAGE，返回 `{"sent": true}`

#### Scenario: 非法 cmd 值
- **WHEN** body 中 cmd 不是 `SetGuard` 或 `ResetGuard`
- **THEN** 返回 `400 Bad Request`

---

### Requirement: 北向 REST 触发录像控制（RecordCmd）
系统 SHALL 提供 REST 接口，向本端或外域设备发送 `<Control><CmdType>DeviceControl</CmdType><RecordCmd>` SIP MESSAGE，用于开始/停止录像。

#### Scenario: 向本端设备发送开始录像
- **WHEN** 调用 `POST /api/devices/local/{gbDeviceId}/control/record`，body `{"cmd": "Record"}`
- **THEN** 系统向该设备发送包含 `<RecordCmd>Record</RecordCmd>` 的 DeviceControl MESSAGE，返回 `{"sent": true}`

#### Scenario: 向外域设备发送停止录像
- **WHEN** 调用 `POST /api/devices/remote/{deviceId}/control/record`，body `{"cmd": "StopRecord"}`
- **THEN** 系统向该设备发送包含 `<RecordCmd>StopRecord</RecordCmd>` 的 DeviceControl MESSAGE，返回 `{"sent": true}`

---

### Requirement: 北向 REST 触发远程重启（TeleBoot）
系统 SHALL 提供 REST 接口，向本端或外域设备发送 `<Control><CmdType>DeviceControl</CmdType><TeleBoot>Boot</TeleBoot>` SIP MESSAGE。

#### Scenario: 向本端设备发送远程重启
- **WHEN** 调用 `POST /api/devices/local/{gbDeviceId}/control/reboot`
- **THEN** 系统向该设备发送包含 `<TeleBoot>Boot</TeleBoot>` 的 DeviceControl MESSAGE，返回 `{"sent": true}`

#### Scenario: 向外域设备发送远程重启
- **WHEN** 调用 `POST /api/devices/remote/{deviceId}/control/reboot`
- **THEN** 系统向该设备发送远程重启 MESSAGE，返回 `{"sent": true}`

---

### Requirement: 南向接收并路由 GuardCmd/RecordCmd/TeleBoot
系统 SHALL 在收到入站 `<Control><CmdType>GuardCmd</CmdType>` 或相关 DeviceControl MESSAGE 时，识别目标设备并路由处理：若目标为本端设备，转发至本端；若目标为外域注册设备，通过 RemoteDeviceMessageForwarder 转发。

#### Scenario: 入站 GuardCmd 路由到本端设备
- **WHEN** 收到 CmdType=GuardCmd 的 SIP MESSAGE，目标 DeviceID 为本端注册设备
- **THEN** DeviceCommandRouter 将命令转发到目标设备，回复 200 OK

#### Scenario: 入站 DeviceControl（录像/重启）路由到外域设备
- **WHEN** 收到 CmdType=DeviceControl（含 RecordCmd 或 TeleBoot）的 SIP MESSAGE，目标为外域设备
- **THEN** DeviceCommandRouter 通过 RemoteDeviceMessageForwarder 转发，回复 200 OK

#### Scenario: 目标设备不存在
- **WHEN** 收到控制命令但 DeviceID 无对应设备
- **THEN** 回复 200 OK（不影响发送方），记录 warn 日志

---

## Source: device-upgrade-command

# device-upgrade-command Specification

## Purpose
定义系统处理 GB/T 28181 `DeviceUpgrade` 命令的行为：北向 REST 接口触发设备固件升级，以及南向收到升级命令时的路由处理。

## Requirements

### Requirement: 北向 REST 触发设备升级（DeviceUpgrade）
系统 SHALL 提供 REST 接口，向本端或外域设备发送 `<Control><CmdType>DeviceUpgrade</CmdType>` SIP MESSAGE，触发设备固件升级。

#### Scenario: 向本端设备发送升级命令
- **WHEN** 调用 `POST /api/devices/local/{gbDeviceId}/control/upgrade`，body 含升级参数（如固件 URL）
- **THEN** 系统向该设备发送包含 `<CmdType>DeviceUpgrade</CmdType>` 的 SIP MESSAGE，返回 `{"sent": true}`

#### Scenario: 向外域设备发送升级命令
- **WHEN** 调用 `POST /api/devices/remote/{deviceId}/control/upgrade`，body 含升级参数
- **THEN** 系统向该设备发送升级 MESSAGE，返回 `{"sent": true}`

#### Scenario: 目标设备不存在
- **WHEN** 调用升级接口但 DeviceID 无对应设备
- **THEN** 返回 `404 Not Found`

---

### Requirement: 南向接收并路由 DeviceUpgrade
系统 SHALL 在收到入站 `<Control><CmdType>DeviceUpgrade</CmdType>` SIP MESSAGE 时，识别目标设备并路由处理。

#### Scenario: 入站 DeviceUpgrade 路由到本端设备
- **WHEN** 收到 CmdType=DeviceUpgrade 的 SIP MESSAGE，目标 DeviceID 为本端注册设备
- **THEN** DeviceCommandRouter 将命令转发到目标设备，回复 200 OK

#### Scenario: 入站 DeviceUpgrade 路由到外域设备
- **WHEN** 收到 CmdType=DeviceUpgrade 的 SIP MESSAGE，目标 DeviceID 为外域设备
- **THEN** DeviceCommandRouter 通过 RemoteDeviceMessageForwarder 转发，回复 200 OK

#### Scenario: 目标设备不存在
- **WHEN** 收到 DeviceUpgrade 命令但 DeviceID 无对应设备
- **THEN** 回复 200 OK，记录 warn 日志

---

## Source: snapshot-command

# snapshot-command Specification

## Purpose
定义系统处理 GB/T 28181 `SnapShotConfig` 命令的行为：北向 REST 接口触发抓图，以及南向收到抓图命令时的路由处理。

## Requirements

### Requirement: 北向 REST 触发抓图（SnapShotConfig）
系统 SHALL 提供 REST 接口，向本端或外域设备发送 `<Control><CmdType>SnapShotConfig</CmdType>` SIP MESSAGE，触发设备抓图。

#### Scenario: 向本端设备发送抓图命令
- **WHEN** 调用 `POST /api/devices/local/{gbDeviceId}/control/snapshot`
- **THEN** 系统向该设备发送包含 `<CmdType>SnapShotConfig</CmdType>` 的 SIP MESSAGE，返回 `{"sent": true}`

#### Scenario: 向外域设备发送抓图命令
- **WHEN** 调用 `POST /api/devices/remote/{deviceId}/control/snapshot`
- **THEN** 系统向该设备发送抓图 MESSAGE，返回 `{"sent": true}`

#### Scenario: 目标设备不存在
- **WHEN** 调用抓图接口但 DeviceID 无对应设备
- **THEN** 返回 `404 Not Found`

---

### Requirement: 南向接收并路由 SnapShotConfig
系统 SHALL 在收到入站 `<Control><CmdType>SnapShotConfig</CmdType>` SIP MESSAGE 时，识别目标设备并路由处理。

#### Scenario: 入站 SnapShotConfig 路由到本端设备
- **WHEN** 收到 CmdType=SnapShotConfig 的 SIP MESSAGE，目标 DeviceID 为本端注册设备
- **THEN** DeviceCommandRouter 将命令转发到目标设备，回复 200 OK

#### Scenario: 入站 SnapShotConfig 路由到外域设备
- **WHEN** 收到 CmdType=SnapShotConfig 的 SIP MESSAGE，目标 DeviceID 为外域设备
- **THEN** DeviceCommandRouter 通过 RemoteDeviceMessageForwarder 转发，回复 200 OK

#### Scenario: 目标设备不存在
- **WHEN** 收到 SnapShotConfig 命令但 DeviceID 无对应设备
- **THEN** 回复 200 OK，记录 warn 日志

---

## Source: device-info-query

## Requirements

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

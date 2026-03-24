## ADDED Requirements

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

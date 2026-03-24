## ADDED Requirements

### Requirement: 预置位查询接口
系统 SHALL 通过 GB/T 28181 `PresetQuery` SIP MESSAGE 查询目标设备的预置位列表，并以 JSON 返回。

#### Scenario: 查询预置位列表成功
- **WHEN** 客户端发送 `GET /api/devices/{type}/{deviceId}/ptz/preset`
- **THEN** 系统发送 `CmdType=PresetQuery` SIP MESSAGE，等待设备响应，返回预置位列表 JSON

#### Scenario: 查询超时
- **WHEN** 设备 10 秒内未响应
- **THEN** 系统返回 HTTP 504

### Requirement: 预置位调用接口
系统 SHALL 通过 `PTZCmd` 发送调用预置位命令，使球机转向指定预置位。

#### Scenario: 调用预置位
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/preset/call`，body 为 `{ "presetIndex": 1 }`
- **THEN** 系统发送 DeviceControl PTZCmd（调用预置位命令码），返回 `{"success": true}`

### Requirement: 预置位设置接口
系统 SHALL 通过 `PTZCmd` 发送设置预置位命令，将当前镜头位置保存为指定编号的预置位。

#### Scenario: 设置预置位
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/preset/set`，body 为 `{ "presetIndex": 1, "presetName": "大门" }`
- **THEN** 系统发送 DeviceControl PTZCmd（设置预置位命令码）及 PTZCmdParams，返回 `{"success": true}`

### Requirement: 预置位删除接口
系统 SHALL 通过 `PTZCmd` 发送删除预置位命令。

#### Scenario: 删除预置位
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/preset/delete`，body 为 `{ "presetIndex": 1 }`
- **THEN** 系统发送 DeviceControl PTZCmd（删除预置位命令码），返回 `{"success": true}`

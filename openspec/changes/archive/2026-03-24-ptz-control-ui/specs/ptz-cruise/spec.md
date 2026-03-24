## ADDED Requirements

### Requirement: 巡航轨迹列表查询接口
系统 SHALL 通过 GB/T 28181 `CruiseTrackListQuery` SIP MESSAGE 查询目标设备支持的巡航轨迹列表。

#### Scenario: 查询巡航轨迹列表成功
- **WHEN** 客户端发送 `GET /api/devices/{type}/{deviceId}/ptz/cruise`
- **THEN** 系统发送 `CmdType=CruiseTrackListQuery` SIP MESSAGE，等待设备响应，返回轨迹列表 JSON

#### Scenario: 查询超时
- **WHEN** 设备 10 秒内未响应
- **THEN** 系统返回 HTTP 504

### Requirement: 巡航轨迹详情查询接口
系统 SHALL 通过 `CruiseTrackQuery` SIP MESSAGE 查询指定编号的巡航轨迹详情（包含预置位序列）。

#### Scenario: 查询指定轨迹详情
- **WHEN** 客户端发送 `GET /api/devices/{type}/{deviceId}/ptz/cruise/{number}`
- **THEN** 系统发送 `CmdType=CruiseTrackQuery` 携带 `Number` 字段，返回轨迹详情 JSON

### Requirement: 巡航启动/停止控制接口
系统 SHALL 通过 `PTZCmd` 发送巡航启动或停止命令，并支持携带轨迹名称参数。

#### Scenario: 启动巡航
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/cruise/start`，body 为 `{ "trackName": "轨迹1" }`
- **THEN** 系统发送 DeviceControl PTZCmd（巡航启动命令码）及 PTZCmdParams.CruiseTrackName，返回 `{"success": true}`

#### Scenario: 停止巡航
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/cruise/stop`
- **THEN** 系统发送 DeviceControl PTZCmd（巡航停止命令码），返回 `{"success": true}`

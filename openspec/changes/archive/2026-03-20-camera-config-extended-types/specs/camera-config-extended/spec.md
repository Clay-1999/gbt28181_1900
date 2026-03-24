## ADDED Requirements

### Requirement: VideoRecordPlan 配置查询与下发
系统 SHALL 支持通过 SIP ConfigDownload/DeviceConfig MESSAGE 查询和修改相机的录像计划配置。

#### Scenario: 查询 VideoRecordPlan
- **WHEN** 客户端发送 `GET /api/devices/local/{gbDeviceId}/config/video-record-plan`
- **THEN** 返回包含 `recordMethod`、`streamType` 字段的 JSON 对象

#### Scenario: 下发 VideoRecordPlan
- **WHEN** 客户端发送 `PUT /api/devices/local/{gbDeviceId}/config/video-record-plan`，body 含 `recordMethod`、`streamType`
- **THEN** 系统通过 SIP DeviceConfig MESSAGE 下发配置，返回 `{"success": true/false}`

### Requirement: VideoAlarmRecord 配置查询与下发
系统 SHALL 支持查询和修改相机的报警录像配置。

#### Scenario: 查询 VideoAlarmRecord
- **WHEN** 客户端发送 `GET /api/devices/local/{gbDeviceId}/config/video-alarm-record`
- **THEN** 返回包含 `preRecordTime`、`alarmRecordTime` 字段的 JSON 对象

#### Scenario: 下发 VideoAlarmRecord
- **WHEN** 客户端发送 `PUT /api/devices/local/{gbDeviceId}/config/video-alarm-record`，body 含 `preRecordTime`、`alarmRecordTime`
- **THEN** 系统通过 SIP DeviceConfig MESSAGE 下发配置，返回 `{"success": true/false}`

### Requirement: AlarmReport 配置查询与下发
系统 SHALL 支持查询和修改相机的报警上报配置。

#### Scenario: 查询 AlarmReport
- **WHEN** 客户端发送 `GET /api/devices/local/{gbDeviceId}/config/alarm-report`
- **THEN** 返回包含 `alarmMethod`、`alarmRecordTime`、`preRecordTime` 字段的 JSON 对象

#### Scenario: 下发 AlarmReport
- **WHEN** 客户端发送 `PUT /api/devices/local/{gbDeviceId}/config/alarm-report`，body 含 `alarmMethod`、`alarmRecordTime`、`preRecordTime`
- **THEN** 系统通过 SIP DeviceConfig MESSAGE 下发配置，返回 `{"success": true/false}`

### Requirement: SnapShot 配置查询与下发
系统 SHALL 支持查询和修改相机的抓图配置。

#### Scenario: 查询 SnapShot
- **WHEN** 客户端发送 `GET /api/devices/local/{gbDeviceId}/config/snap-shot`
- **THEN** 返回包含 `snapShotInterval`、`snapShotTimes` 字段的 JSON 对象

#### Scenario: 下发 SnapShot
- **WHEN** 客户端发送 `PUT /api/devices/local/{gbDeviceId}/config/snap-shot`，body 含 `snapShotInterval`、`snapShotTimes`
- **THEN** 系统通过 SIP DeviceConfig MESSAGE 下发配置，返回 `{"success": true/false}`

### Requirement: 外域设备同步支持 4 种扩展类型
系统 SHALL 对外域设备提供与本端设备相同的 4 种扩展配置类型 GET/PUT 端点。

#### Scenario: 外域设备查询 SnapShot
- **WHEN** 客户端发送 `GET /api/devices/remote/{deviceId}/config/snap-shot`
- **THEN** 系统发送 SIP ConfigDownload MESSAGE，超时返回 HTTP 504，成功返回配置 JSON

## ADDED Requirements

### Requirement: 处理本端设备 DeviceConfig 配置
系统 SHALL 响应对端发来的 DeviceConfig 配置命令，对本端 IVS1900 相机调用 ivs1900 REST API 执行配置，组装 GB/T 28181 DeviceConfig Response 回复。对所有 11 种 DeviceConfig 子命令均返回合法响应；ivs1900 无对应接口的子命令静默忽略并回复 Result=OK。

#### Scenario: 配置 BasicParam（相机名称）
- **WHEN** 收到 DeviceConfig，含 BasicParam 子命令（含 Name 字段），DeviceID 在 `ivs1900_camera_mapping`
- **THEN** 调用 ivs1900 `PUT /device/camera/name/v1.0` 修改相机名称，成功后回复 `<Response><CmdType>DeviceConfig</CmdType><Result>OK</Result></Response>`

#### Scenario: 配置 SVACEncodeConfig / VideoParamAttribute / VideoRecordPlan / VideoAlarmRecord / PictureMask / FrameMirror / AlarmReport / OSDConfig / SnapShotConfig / SVACDecodeConfig
- **WHEN** 收到 DeviceConfig，子命令为上述任一，DeviceID 在 `ivs1900_camera_mapping`
- **THEN** ivs1900 无对应接口，记录 debug 日志，回复 DeviceConfig Response，Result=OK（静默忽略配置）

#### Scenario: 配置失败（ivs1900 返回错误）
- **WHEN** ivs1900 REST API 返回非 2xx 状态码（BasicParam/Name 修改失败）
- **THEN** 回复 DeviceConfig Response，Result 为 `Error`，记录 error 日志

### Requirement: 透传外域设备 DeviceConfig
系统 SHALL 将外域设备的 DeviceConfig 命令原样转发至对端平台。

#### Scenario: 透传 DeviceConfig 并收到应答
- **WHEN** 收到 DeviceConfig，DeviceID 在 `remote_device`，对端 5 秒内回复
- **THEN** 将对端的 DeviceConfig Response MESSAGE 透传回原始请求方

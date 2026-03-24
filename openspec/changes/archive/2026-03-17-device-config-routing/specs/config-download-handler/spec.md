## ADDED Requirements

### Requirement: 处理本端设备 ConfigDownload 查询
系统 SHALL 响应对端发来的 ConfigDownload 查询命令，对本端 IVS1900 相机调用 ivs1900 REST API 获取配置，组装 GB/T 28181 ConfigDownload Response 回复。对所有 12 种 ConfigType 均返回合法响应；ivs1900 无对应接口的 ConfigType 返回对应空结构体（如 `<SVACDecodeConfig/>`）。

#### Scenario: 查询 BasicParam
- **WHEN** 收到 ConfigDownload，ConfigType=BasicParam，DeviceID 在 `ivs1900_camera_mapping`
- **THEN** 调用 ivs1900 `POST /device/camera/batchconfig/v1.0`，将相机名称、心跳周期等字段组装为 `<Response><CmdType>ConfigDownload</CmdType><BasicParam>...</BasicParam></Response>`，通过 SIP MESSAGE 回复

#### Scenario: 查询 VideoParamOpt
- **WHEN** 收到 ConfigDownload，ConfigType=VideoParamOpt，DeviceID 在 `ivs1900_camera_mapping`
- **THEN** 调用 ivs1900 `POST /device/camera/batchconfig/v1.0`，将编码参数映射为 `<VideoParamOpt>` 字段回复；ivs1900 无对应的字段省略

#### Scenario: 查询 SVACEncodeConfig
- **WHEN** 收到 ConfigDownload，ConfigType=SVACEncodeConfig
- **THEN** 调用 ivs1900 `POST /device/camera/batchconfig/v1.0` 取可映射字段，组装 `<SVACEncodeConfig>` 回复；无对应字段省略

#### Scenario: 查询 SVACDecodeConfig / VideoRecordPlan / VideoAlarmRecord / PictureMask / FrameMirror / AlarmReport / OSDConfig / SnapShotConfig
- **WHEN** 收到 ConfigDownload，ConfigType 为上述任一，DeviceID 在 `ivs1900_camera_mapping`
- **THEN** ivs1900 无对应接口，回复含对应空结构体的 ConfigDownload Response（如 `<SVACDecodeConfig/>`），Result=OK

#### Scenario: 查询 VideoParamAttribute
- **WHEN** 收到 ConfigDownload，ConfigType=VideoParamAttribute
- **THEN** 调用 ivs1900 `POST /device/camera/batchconfig/v1.0` 取视频属性字段，组装 `<VideoParamAttribute>` 回复；无对应字段省略

#### Scenario: 多 ConfigType 同时查询
- **WHEN** ConfigType 字段包含多个类型（以 `/` 分隔，如 `BasicParam/VideoParamOpt`）
- **THEN** 对每种 ConfigType 分别处理，合并到同一 Response 消息中回复

### Requirement: 透传外域设备 ConfigDownload
系统 SHALL 将外域设备的 ConfigDownload 命令原样转发至对端平台。

#### Scenario: 透传 ConfigDownload 并收到应答
- **WHEN** 收到 ConfigDownload，DeviceID 在 `remote_device`，对端 5 秒内回复
- **THEN** 将对端的 ConfigDownload Response MESSAGE 透传回原始请求方

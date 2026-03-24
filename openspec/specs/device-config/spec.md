## Source: config-download-handler

# config-download-handler Specification

## Purpose
TBD - created by archiving change device-config-routing. Update Purpose after archive.
## Requirements
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

---

## Source: device-config-handler

# device-config-handler Specification

## Purpose
TBD - created by archiving change device-config-routing. Update Purpose after archive.
## Requirements
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

---

## Source: device-command-router

# device-command-router Specification

## Purpose
TBD - created by archiving change device-config-routing. Update Purpose after archive.
## Requirements
### Requirement: 按 DeviceID 归属路由 SIP MESSAGE 命令
系统 SHALL 在收到 SIP MESSAGE（CmdType 为 ConfigDownload 或 DeviceConfig）时，解析目标 DeviceID，按以下优先级路由：
1. DeviceID 存在于 `ivs1900_camera_mapping` → 转交本端处理器（调 ivs1900 REST API）
2. DeviceID 存在于 `remote_device` → 转交外域透传处理器（转发给对端平台）
3. 两者均无 → 回复 `SIP/2.0 404 Not Found`

#### Scenario: DeviceID 属于本端 IVS1900 相机
- **WHEN** 收到 ConfigDownload/DeviceConfig SIP MESSAGE，DeviceID 在 `ivs1900_camera_mapping`
- **THEN** 路由至本端处理器，调用 ivs1900 REST API，结果回复对端

#### Scenario: DeviceID 属于外域平台设备
- **WHEN** 收到 ConfigDownload/DeviceConfig SIP MESSAGE，DeviceID 在 `remote_device`
- **THEN** 路由至透传处理器，将原始 MESSAGE 转发至 `remote_device.interconnect_config_id` 对应的对端平台

#### Scenario: DeviceID 未知
- **WHEN** 收到 ConfigDownload/DeviceConfig SIP MESSAGE，DeviceID 既不在 `ivs1900_camera_mapping` 也不在 `remote_device`
- **THEN** 回复 `SIP/2.0 404 Not Found`，Body 为空

### Requirement: 外域设备命令透传
系统 SHALL 将外域设备命令原样转发至对端平台，并透传对端应答。

#### Scenario: 透传成功并收到对端应答
- **WHEN** 外域设备命令转发后，对端平台在 5 秒内回复 SIP 200 OK（含或不含应答 MESSAGE）
- **THEN** 将对端应答透传回原始请求方

#### Scenario: 对端无响应超时
- **WHEN** 外域设备命令转发后，5 秒内未收到对端响应
- **THEN** 回复原始请求方 `SIP/2.0 408 Request Timeout`

### Requirement: 路由 SnapShotConfig 命令
系统 SHALL 在收到 CmdType 为 `SnapShotConfig` 的入站 SIP MESSAGE 时，按设备归属路由至本端或外域处理器。

#### Scenario: SnapShotConfig 路由到本端设备
- **WHEN** 收到 CmdType=SnapShotConfig 的 SIP MESSAGE，DeviceID 在 `ivs1900_camera_mapping`
- **THEN** 路由至本端处理器，回复 200 OK

#### Scenario: SnapShotConfig 路由到外域设备
- **WHEN** 收到 CmdType=SnapShotConfig 的 SIP MESSAGE，DeviceID 在 `remote_device`
- **THEN** 通过 RemoteDeviceMessageForwarder 转发，回复 200 OK

#### Scenario: SnapShotConfig 目标设备未知
- **WHEN** 收到 CmdType=SnapShotConfig 的 SIP MESSAGE，DeviceID 既不在 `ivs1900_camera_mapping` 也不在 `remote_device`
- **THEN** 回复 `SIP/2.0 404 Not Found`，Body 为空

### Requirement: 路由 DeviceUpgrade 命令
系统 SHALL 在收到 CmdType 为 `DeviceUpgrade` 的入站 SIP MESSAGE 时，按设备归属路由至本端或外域处理器。

#### Scenario: DeviceUpgrade 路由到本端设备
- **WHEN** 收到 CmdType=DeviceUpgrade 的 SIP MESSAGE，DeviceID 在 `ivs1900_camera_mapping`
- **THEN** 路由至本端处理器，回复 200 OK

#### Scenario: DeviceUpgrade 路由到外域设备
- **WHEN** 收到 CmdType=DeviceUpgrade 的 SIP MESSAGE，DeviceID 在 `remote_device`
- **THEN** 通过 RemoteDeviceMessageForwarder 转发，回复 200 OK

#### Scenario: DeviceUpgrade 目标设备未知
- **WHEN** 收到 CmdType=DeviceUpgrade 的 SIP MESSAGE，DeviceID 既不在 `ivs1900_camera_mapping` 也不在 `remote_device`
- **THEN** 回复 `SIP/2.0 404 Not Found`，Body 为空

---

## Source: camera-config-extended

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

---

## Source: ivs1900-set-config-rmw

## ADDED Requirements

### Requirement: set 接口内置 read-modify-write
`Ivs1900DeviceConfigClient` 的 setStreamConfig / setOsdConfig / setVideoMask 接口 SHALL 在内部先调用对应 GET 接口获取设备当前完整配置，将传入的 patch 字段浅层合并后再下发，调用方无需自行执行 GET。

#### Scenario: GET 成功，patch 合并后下发
- **WHEN** 调用 setStreamConfig(cameraCode, patch)，且 getStreamConfig 返回非空结果
- **THEN** 系统将 patch 字段覆盖到 GET 结果对应字段上，以合并后的完整参数调用 doSetDeviceConfig

#### Scenario: GET 失败时返回 false
- **WHEN** 调用 setStreamConfig / setOsdConfig / setVideoMask，且对应 GET 接口返回 null 或结构不完整
- **THEN** 方法立即返回 false，不执行下发

### Requirement: DeviceConfigHandler 不再重复执行 GET
`DeviceConfigHandler` 的 applyFrameMirror / applyOsdConfig / applyPictureMask SHALL 直接构造 patch JsonNode 传给对应 set 接口，不再自行调用 GET 接口或手动 merge。

#### Scenario: applyOsdConfig 简化调用
- **WHEN** 收到 DeviceConfig OSDConfig 命令
- **THEN** handler 从 XML 中提取 OSDEnabled / OSDTime / OSDFontSize 构造 patch，直接调用 setOsdConfig(cameraCode, patch)

#### Scenario: applyPictureMask 简化调用
- **WHEN** 收到 DeviceConfig PictureMask 命令
- **THEN** handler 从 XML 中提取 MaskEnabled 等字段构造 patch，直接调用 setVideoMask(cameraCode, patch)

#### Scenario: applyFrameMirror 简化调用
- **WHEN** 收到 DeviceConfig FrameMirror 命令
- **THEN** handler 构造含 frameMirrorMode 字段的 patch，直接调用 setStreamConfig(cameraCode, patch)

---

## Source: local-device-config-api

## ADDED Requirements

### Requirement: 查询本端相机配置
系统 SHALL 为每种配置类型提供独立的 GET 接口：
- `GET /api/devices/local/{gbDeviceId}/config/video-param`
- `GET /api/devices/local/{gbDeviceId}/config/osd`
- `GET /api/devices/local/{gbDeviceId}/config/picture-mask`
- `GET /api/devices/local/{gbDeviceId}/config/frame-mirror`

各接口返回对应相机的当前配置参数（JSON）。

#### Scenario: 查询存在的相机配置
- **WHEN** 请求 `GET /api/devices/local/{gbDeviceId}/config/osd`，且 gbDeviceId 对应的相机存在且 IVS1900 返回数据
- **THEN** 响应 200，body 为包含 OSD 配置字段的 JSON 对象

#### Scenario: 相机不存在
- **WHEN** 请求任意配置 GET 接口，且 gbDeviceId 不存在
- **THEN** 响应 404

#### Scenario: IVS1900 返回空
- **WHEN** IVS1900 接口返回 null 或结构不完整
- **THEN** 响应 200，body 为空 JSON 对象 `{}`

### Requirement: 下发本端相机配置
系统 SHALL 为每种配置类型提供独立的 PUT 接口：
- `PUT /api/devices/local/{gbDeviceId}/config/video-param`
- `PUT /api/devices/local/{gbDeviceId}/config/osd`
- `PUT /api/devices/local/{gbDeviceId}/config/picture-mask`
- `PUT /api/devices/local/{gbDeviceId}/config/frame-mirror`

各接口将请求 body（JSON patch）下发至 IVS1900。

#### Scenario: 成功下发配置
- **WHEN** 请求 `PUT /api/devices/local/{gbDeviceId}/config/osd`，body 包含合法字段
- **THEN** 响应 200，body 为 `{"success": true}`

#### Scenario: 下发失败
- **WHEN** IVS1900 返回非 0 resultCode 或 GET 失败
- **THEN** 响应 200，body 为 `{"success": false}`

---

## Source: remote-device-config-api

## ADDED Requirements

### Requirement: 查询外域设备配置
系统 SHALL 为每种配置类型提供独立的 GET 接口：
- `GET /api/devices/remote/{deviceId}/config/video-param`
- `GET /api/devices/remote/{deviceId}/config/osd`
- `GET /api/devices/remote/{deviceId}/config/picture-mask`
- `GET /api/devices/remote/{deviceId}/config/frame-mirror`

各接口通过发送对应 GBT28181 ConfigDownload SIP MESSAGE 查询外域设备配置，同步等待响应（最长 10 秒），返回解析后的 JSON。

#### Scenario: 查询成功
- **WHEN** 请求 `GET /api/devices/remote/{deviceId}/config/video-param`，且外域平台在 10s 内回复响应 MESSAGE
- **THEN** 响应 200，body 为包含配置字段的 JSON 对象

#### Scenario: 外域设备不存在
- **WHEN** deviceId 在本地 remote_device 表中不存在
- **THEN** 响应 404

#### Scenario: SIP 响应超时
- **WHEN** 外域平台 10s 内未回复
- **THEN** 响应 504，body 包含超时说明

### Requirement: 下发外域设备配置
系统 SHALL 为每种配置类型提供独立的 PUT 接口：
- `PUT /api/devices/remote/{deviceId}/config/video-param`
- `PUT /api/devices/remote/{deviceId}/config/osd`
- `PUT /api/devices/remote/{deviceId}/config/picture-mask`
- `PUT /api/devices/remote/{deviceId}/config/frame-mirror`

各接口通过发送对应 GBT28181 DeviceConfig SIP MESSAGE 下发配置，同步等待响应（最长 10 秒）。

#### Scenario: 下发成功
- **WHEN** 请求 `PUT /api/devices/remote/{deviceId}/config/osd`，外域平台回复 Result=OK
- **THEN** 响应 200，body 为 `{"success": true}`

#### Scenario: 下发失败或超时
- **WHEN** 外域平台回复 Result=Error 或 10s 超时
- **THEN** 响应 200，body 为 `{"success": false}`（超时时响应 504）

### Requirement: 外域配置响应 MESSAGE 路由
系统 SHALL 识别外域平台主动回复的配置响应 MESSAGE（CmdType=ConfigDownload/DeviceConfig），将其路由至等待中的 future，而非作为新请求处理。

#### Scenario: 响应 MESSAGE 触发 future
- **WHEN** 收到来自外域平台的 MESSAGE，其 Call-ID 与本地 pending 中的 key 匹配
- **THEN** 对应 CompletableFuture 被 complete，REST 接口返回结果

---

## Source: camera-config-dialog

## ADDED Requirements

### Requirement: 设备列表配置入口
设备列表页（本端和外域）SHALL 在每行提供"配置"操作按钮，点击后弹出该设备的配置对话框。

#### Scenario: 点击配置按钮
- **WHEN** 用户点击设备行的"配置"按钮
- **THEN** 弹出配置对话框，自动加载当前配置，显示加载状态

### Requirement: 配置对话框分 tab 展示
配置对话框 SHALL 使用 tab 页分别展示 VideoParamAttribute、OSDConfig、PictureMask、FrameMirror 四种配置类型，切换 tab 时自动加载对应配置。

#### Scenario: 切换配置 tab
- **WHEN** 用户切换到 OSDConfig tab
- **THEN** 系统调用 GET 接口加载 OSD 配置，填充表单字段

#### Scenario: 配置加载失败
- **WHEN** GET 接口返回错误或超时
- **THEN** 显示错误提示，表单字段置空

### Requirement: 配置表单保存
配置对话框 SHALL 提供"保存"按钮，将表单当前值通过 PUT 接口下发，并显示操作结果。

#### Scenario: 保存成功
- **WHEN** 用户修改字段后点击"保存"，PUT 接口返回 `{"success": true}`
- **THEN** 显示成功提示，对话框保持打开

#### Scenario: 保存失败
- **WHEN** PUT 接口返回 `{"success": false}` 或网络错误
- **THEN** 显示失败提示，表单数据保留

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

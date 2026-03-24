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

## ADDED Requirements

### Requirement: 本端设备视频流 REST 端点
系统 SHALL 在 `DeviceController` 中提供本端设备视频流启动和停止端点。

#### Scenario: stream/start 端点存在
- **WHEN** 客户端发送 `POST /api/devices/local/{gbDeviceId}/stream/start`
- **THEN** 系统处理请求并返回 `{"streamUrl": "<url>"}` 或错误响应

#### Scenario: stream/stop 端点存在
- **WHEN** 客户端发送 `POST /api/devices/local/{gbDeviceId}/stream/stop`
- **THEN** 系统处理请求并返回 HTTP 200

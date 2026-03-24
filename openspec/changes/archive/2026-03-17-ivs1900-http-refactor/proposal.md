## Why

当前 `ivs1900` 包下与 IVS1900 交互的代码存在两个问题：

1. **缺少统一 HTTP 请求封装**：`Ivs1900SessionManager`、`Ivs1900SyncService`、`Ivs1900DeviceConfigClient` 各自直接调用 `restTemplate`，401 重登逻辑分散在多处，行为不一致。
2. **使用 `Map` 或裸 `ObjectNode` 拼装请求体**：缺乏类型安全，字段名拼写错误难以发现，可读性差。

## What Changes

- 新增 `Ivs1900HttpClient`：统一封装 GET/POST/PUT/DELETE，内置 session 注入和 401 重登重试
- 新增 4 个请求 DTO：`LoginRequest`、`SdcCapabilityRequest`、`CameraNameRequest`、`SetDeviceConfigRequest`
- 迁移 `Ivs1900SessionManager`、`Ivs1900SyncService`、`Ivs1900DeviceConfigClient` 中所有直接 `restTemplate` 调用

## Capabilities

### New Capabilities

- `ivs1900-http-client`: 统一 HTTP 客户端，封装认证、重试、日志

### Modified Capabilities

- `ivs1900-session`: 登录请求仍直接用 `restTemplate`（避免循环依赖），登出和保活改用 `Ivs1900HttpClient`
- `ivs1900-sync`: 拉取摄像机列表和在线状态改用 `Ivs1900HttpClient`
- `ivs1900-device-config`: 所有读写接口改用 `Ivs1900HttpClient`，移除内部重登循环

## Impact

- 新增：`ivs1900/Ivs1900HttpClient.java`
- 新增：`ivs1900/dto/LoginRequest.java`、`SdcCapabilityRequest.java`、`CameraNameRequest.java`、`SetDeviceConfigRequest.java`
- 修改：`Ivs1900SessionManager.java`、`Ivs1900SyncService.java`、`Ivs1900DeviceConfigClient.java`

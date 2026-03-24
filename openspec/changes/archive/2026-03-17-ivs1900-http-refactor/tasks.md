# Tasks

- [x] 新增 `LoginRequest` DTO（`userName`, `password`, `timeout`）
- [x] 新增 `SdcCapabilityRequest` DTO（`cameraCodeList`, `capabilityType`）
- [x] 新增 `CameraNameRequest` DTO（`cameraCode`, `newCameraName`）
- [x] 新增 `SetDeviceConfigRequest` DTO（`deviceCode`, `configType`, `configItem: JsonNode`）
- [x] 新增 `Ivs1900HttpClient`，实现 `get`、`post`、`put`、`delete` 方法，内置 session 注入和 401 重登重试
- [x] 迁移 `Ivs1900SessionManager`：登录改用 `LoginRequest`，登出和保活改用 `Ivs1900HttpClient`
- [x] 迁移 `Ivs1900SyncService`：`fetchCameraList` 和 `fetchOnlineStatus` 改用 `Ivs1900HttpClient.get()`
- [x] 迁移 `Ivs1900DeviceConfigClient`：`doGet`、`getSdcCapability`、`setCameraName`、`doSetDeviceConfig` 改用 `Ivs1900HttpClient`，移除内部重登循环
- [x] 编译通过，运行 `tmp_sip_config_test.py` 验证 18/18 PASS

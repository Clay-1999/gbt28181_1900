## 1. DTO

- [x] 1.1 新建 `SnapshotRequest.java`：`snapNum`(int)、`interval`(int)、`uploadAddr`(String)、`resolution`(String)
- [x] 1.2 新建 `UpgradeRequest.java`：`firmwareId`(String)、`firmwareAddr`(String)

## 2. DeviceControlService 扩展

- [x] 2.1 新增 `sendSnapshot(type, deviceId, SnapshotRequest)` 方法：构造 SnapShotConfig XML，调 `sendMessage()`
- [x] 2.2 新增 `sendUpgrade(type, deviceId, UpgradeRequest)` 方法：构造 DeviceUpgrade XML，调 `sendMessage()`

## 3. DeviceController 端点

- [x] 3.1 新增 `POST /api/devices/local/{gbDeviceId}/snapshot` 和 `POST /api/devices/remote/{deviceId}/snapshot`
- [x] 3.2 新增 `POST /api/devices/local/{gbDeviceId}/upgrade` 和 `POST /api/devices/remote/{deviceId}/upgrade`

## 4. DeviceCommandRouter 南向路由

- [x] 4.1 新增 `SnapShotConfig` 入站路由分支：回复 200 OK，路由本端/外域/未知
- [x] 4.2 新增 `DeviceUpgrade` 入站路由分支：回复 200 OK，路由本端/外域/未知

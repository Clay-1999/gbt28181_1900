## 1. 9.10 校时（Date 头域）

- [x] 1.1 修改 `SipRegistrationServer.sendResponse()`：仅在 statusCode=200 时，构造 `DateHeader`（北京时间，格式 `YYYY-MM-DDTHH:MM:SS`）并加入响应

## 2. 9.5 DeviceInfo/DeviceStatus 查询

- [x] 2.1 新建 `DeviceInfoQueryService.java`：维护 `ConcurrentHashMap<String, CompletableFuture<String>> pending`，实现 `queryDeviceInfo(deviceId, type)` 方法（发送 MESSAGE，10s 等待）和 `onResponse(sn, xml)` 方法（complete future）
- [x] 2.2 在 `DeviceCommandRouter.java` 中新增 DeviceInfo/DeviceStatus Response MESSAGE 的路由分支
- [x] 2.3 在 `DeviceController.java` 中新增 4 个端点

## 3. 9.3 DeviceControl 命令（北向发送）

- [x] 3.1 在 `DeviceController.java` 中新增 6 个端点（/control/guard、/control/record、/control/reboot）
- [x] 3.2 新建 `DeviceControlRequest.java` DTO（含 `cmd` 字段）

## 4. 9.3 DeviceControl 命令（南向接收路由）

- [x] 4.1 在 `DeviceCommandRouter.java` 中补充 GuardCmd 入站路由分支
- [x] 4.2 在 `DeviceCommandRouter.java` 中补充 DeviceControl（RecordCmd/TeleBoot）入站路由分支

## 1. 重构 SipInviteService

- [x] 1.1 新增 `startStream(InterconnectConfig target, String deviceId)` 重载方法，将目标配置作为参数
- [x] 1.2 原有 `startStream(String deviceId)` 改为内部查找配置后调用新重载，保持外域流功能不变

## 2. SipRegistrationServer 暴露注册记录

- [x] 2.1 将内部 `RegistrationEntry` record 改为 package-visible 或新增 `getRegistrationEntry(String sipId)` 公共方法，返回注册的 IP、端口信息

## 3. 新增 LocalDeviceStreamService

- [x] 3.1 新建 `LocalDeviceStreamService`，依赖 `Ivs1900CameraMappingRepository`、`Ivs1900InterconnectConfigRepository`、`SipRegistrationServer`、`SipInviteService`
- [x] 3.2 实现 `startStream(String gbDeviceId)`：查找 camera mapping 取 `ivsCameraId`，从注册记录取 IP/端口，构造 `InterconnectConfig`，调用 `SipInviteService.startStream(target, ivsCameraId)`，streamId 前缀用 `local_`
- [x] 3.3 实现 `stopStream(String gbDeviceId)`：查找 camera mapping 取 `ivsCameraId`，调用 `SipInviteService.stopStream(ivsCameraId)`

## 4. DeviceController 新增端点

- [x] 4.1 新增 `POST /api/devices/local/{gbDeviceId}/stream/start`，调用 `LocalDeviceStreamService.startStream`，返回 `{"streamUrl": "..."}` 或错误
- [x] 4.2 新增 `POST /api/devices/local/{gbDeviceId}/stream/stop`，调用 `LocalDeviceStreamService.stopStream`，幂等处理（无会话时返回 200）

## 5. 前端 DevicesView.vue

- [x] 5.1 本端设备表格操作列新增"播放"按钮，点击调用 `openStream(row, 'local')`
- [x] 5.2 `openStream` 方法根据设备类型选择 API 路径：本端用 `/api/devices/local/{gbDeviceId}/stream/start`，外域用 `/api/devices/remote/{deviceId}/stream/start`
- [x] 5.3 `closeStream` 方法同样根据设备类型选择 stop API 路径

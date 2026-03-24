## Why

当前平台已支持外域设备的视频流拉取，但本端设备（IVS1900 相机）无法在 Web 界面预览实时视频。IVS1900 已通过 GB/T 28181 SIP 注册到本平台，平台作为上级平台可向其发起 INVITE 拉流，复用已有的 ZLMediaKit 集成和 SIP INVITE 基础设施。

## What Changes

- 新增 `POST /api/devices/local/{gbDeviceId}/stream/start` 接口：向 IVS1900 相机发起 GB/T 28181 SIP INVITE，返回 HTTP-FLV 播放地址
- 新增 `POST /api/devices/local/{gbDeviceId}/stream/stop` 接口：发送 SIP BYE 停止流，释放资源
- 复用 `SipInviteService`、`StreamSessionStore`、`ZLMediaKitClient`（已由外域流功能实现）
- 前端 DevicesView.vue 本端设备列表新增"播放"按钮，复用外域设备的播放对话框逻辑

## Capabilities

### New Capabilities

- `local-device-stream`: 本端 IVS1900 相机实时视频流拉取与播放，包含 SIP INVITE/BYE 流程（平台作为 UAC 向 IVS1900 发起）、ZLMediaKit 集成、流会话生命周期管理

### Modified Capabilities

- `device-list-api`: 本端设备列表新增 stream/start 和 stream/stop 两个端点

## Impact

- 新增后端类：`LocalDeviceStreamController`（或在现有 `DeviceController` 中扩展）
- 修改：`DevicesView.vue`（本端设备列表新增播放按钮，复用现有播放对话框）
- 复用：`SipInviteService`、`StreamSessionStore`、`ZLMediaKitClient`（无需修改）
- IVS1900 相机需已注册到平台（`SipRegistrationServer` 中有活跃注册记录）

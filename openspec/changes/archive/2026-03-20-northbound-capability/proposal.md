## Why

作为 GB/T 28181 互联平台，我们的南向已具备完整能力（注册、目录、视频流、PTZ、配置、录像），但北向存在三处关键缺口：目录 NOTIFY 从未真正发送（TODO 未实现），上级平台无法通过标准流程发现我方设备；本端 IVS1900 相机无法被上级通过 INVITE 拉流；PTZ 命令无法从上级透传到本端相机。这些缺口导致平台无法作为完整的 GB/T 28181 下级域接入任何上级平台。

## What Changes

- **修复目录 NOTIFY 发送**：在 `CatalogSubscribeHandler.sendCatalogNotify()` 中实现真正的 SIP NOTIFY 请求发送，使上级平台 SUBSCRIBE 后能收到本方完整设备目录
- **支持注册成功后主动推送目录**：`SipRegistrationClient` 注册成功后，主动向上级发送 NOTIFY 推送目录（无需等待 SUBSCRIBE）
- **本端相机北向视频流**：上级平台发来 INVITE（目标为本端 IVS1900 相机 gbDeviceId）时，通过 ZLMediaKit 拉起 IVS1900 流并返回 SDP，实现本端相机对上级可播放
- **北向 PTZ 透传（本端相机）**：接收上级发来的 `DeviceControl/PTZCmd` SIP MESSAGE，路由到 `PtzService` 执行，支持本端相机 PTZ

## Capabilities

### New Capabilities

- `northbound-catalog-notify`: 北向目录 NOTIFY 发送——响应上级 SUBSCRIBE 和注册后主动推送目录
- `northbound-local-stream`: 本端相机北向视频流——响应上级 INVITE 拉取 IVS1900 本端相机流
- `northbound-ptz-receive`: 北向 PTZ 命令接收——接收上级 DeviceControl 消息并路由到本端/外域设备

### Modified Capabilities

- `catalog-subscribe`: 新增 NOTIFY 实际发送逻辑（原实现为 TODO 占位）
- `sip-client-registration`: 注册成功后触发主动目录推送

## Impact

- `sip/CatalogSubscribeHandler.java`：实现 NOTIFY 构造与发送
- `sip/SipRegistrationClient.java`：注册成功回调触发目录推送
- `sip/GbtSipListener.java`：新增 DeviceControl 请求处理分支
- `service/LocalDeviceStreamService.java`（或新增）：北向 INVITE 触发本端 IVS1900 拉流
- `sip/SipInviteService.java`：扩展以支持本端相机的 INVITE 响应
- 依赖：`ZLMediaKitClient`（已有），`Ivs1900CameraMappingRepository`（已有），`PtzService`（已有）

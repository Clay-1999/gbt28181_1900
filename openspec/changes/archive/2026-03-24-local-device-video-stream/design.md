## Context

外域设备视频流功能已实现完整的 GB/T 28181 SIP INVITE/ACK/BYE 流程，包括 `SipInviteService`、`StreamSessionStore`、`ZLMediaKitClient`、`SipMessageSender.sendInvite/sendAck/sendBye`。

本端设备（IVS1900）已通过 GB/T 28181 SIP 注册到平台（`SipRegistrationServer` 维护注册表），平台作为上级平台。向 IVS1900 发起 INVITE 拉流的 SIP 流程与外域设备完全相同，区别仅在于目标地址来源：外域设备从 `InterconnectConfig` 取，本端设备从 `SipRegistrationServer` 的注册记录取（IP、端口、SIP ID）。

## Goals / Non-Goals

**Goals:**
- 复用现有 `SipInviteService`、`StreamSessionStore`、`ZLMediaKitClient` 实现本端设备拉流
- 新增 REST 接口 `POST /api/devices/local/{gbDeviceId}/stream/start` 和 `stream/stop`
- 前端本端设备列表新增"播放"按钮，复用现有播放对话框

**Non-Goals:**
- 不修改 `SipInviteService` 核心逻辑
- 不支持录像回放
- 不支持 PTZ 控制

## Decisions

### 决策 1：复用 SipInviteService，不新建本端专用服务

`SipInviteService.startStream(deviceId)` 当前硬编码从 `RemoteDeviceRepository` 和 `InterconnectConfigRepository` 查找目标配置。本端设备的目标配置来源不同（注册记录而非互联配置），因此需要重构 `SipInviteService`，将目标配置作为参数传入，或新增重载方法。

**选择**：在 `SipInviteService` 中新增 `startStream(InterconnectConfig target, String deviceId)` 重载，原有方法内部调用此重载。本端流控制器直接调用带参数的重载，传入从注册记录构造的 `InterconnectConfig`。

**理由**：最小改动，不破坏外域流功能，不引入新的抽象层。

### 决策 2：从 SipRegistrationServer 获取 IVS1900 注册地址

IVS1900 注册时，`SipRegistrationServer` 记录了其 IP、端口、SIP ID。需要暴露一个查询方法 `getRegisteredAddress(sipId)` 返回注册信息，供本端流控制器构造 `InterconnectConfig`。

**选择**：在 `SipRegistrationServer` 中新增 `getRegistrationEntry(sipId)` 方法，返回 `RegistrationEntry`（已有内部 record，包含 ip、port、sipId）。

### 决策 3：本端流 deviceId 使用 gbDeviceId

REST 接口路径参数是 `gbDeviceId`（平台分配的国标 ID），`StreamSessionStore` 的 key 也用 `gbDeviceId`，与外域设备的 `deviceId` 保持一致的 key 语义。

SIP INVITE 的 `Request-URI` 中的 deviceId 使用 IVS1900 的 `ivsCameraId`（其原生 SIP ID），与 `Ivs1900SipConfigService` 保持一致。

### 决策 4：前端复用播放对话框

外域设备已有完整的播放对话框（`streamDialogVisible`、`initFlvPlayer`、`closeStream`）。本端设备只需在操作列新增"播放"按钮，调用 `/api/devices/local/{gbDeviceId}/stream/start`，其余逻辑完全复用。

## Risks / Trade-offs

- [IVS1900 未注册] → `getRegistrationEntry` 返回空，接口返回 404；前端提示"设备未注册"
- [IVS1900 不支持 INVITE] → INVITE 超时 10s，接口返回 503；与外域设备超时处理一致
- [本端/外域 streamId 冲突] → 本端 streamId 前缀用 `local_`，外域用 `gb28181_`，避免 ZLM 中冲突

## Migration Plan

1. 重构 `SipInviteService`：新增带 target 参数的重载
2. `SipRegistrationServer` 暴露注册记录查询方法
3. 新增 `LocalDeviceStreamService`（或在 `DeviceController` 中直接实现）
4. `DeviceController` 新增 stream/start、stream/stop 端点
5. 前端本端设备列表新增播放按钮

## Context

本平台已完整实现南向 GB/T 28181 能力（注册、目录、视频流、PTZ、配置、录像），北向在以下三处存在缺口：

1. `CatalogSubscribeHandler.sendCatalogNotify()` 中 XML 已构建但有 `// TODO`，NOTIFY SIP 消息从未发送，导致上级平台订阅后无法获取本方设备列表
2. 上级平台发来 INVITE 请求本端 IVS1900 相机时，`SipInviteService` 无处理逻辑，本端相机对上级不可见、不可播放
3. 上级平台发来 `DeviceControl/PTZCmd` 时，`GbtSipListener` 和 `DeviceCommandRouter` 无路由分支，本端相机 PTZ 无法受上级控制

现有复用基础：`SipMessageSender`（发 SIP MESSAGE）、`SipInviteService`（管理 INVITE 会话）、`ZLMediaKitClient`（本端拉流）、`PtzService`（本端 PTZ）、`LocalDeviceStreamService`（本端流管理）均已实现，北向能力主要是**接线**工作。

## Goals / Non-Goals

**Goals:**
- 修复目录 NOTIFY 实际发送，上级 SUBSCRIBE 后能收到完整设备列表
- 注册成功后主动向上级推送目录（无需等 SUBSCRIBE）
- 上级 INVITE 本端相机时，通过 ZLMediaKit 拉起流并返回正确 SDP
- 上级发 `DeviceControl/PTZCmd` 时，路由到 `PtzService` 执行本端或外域相机 PTZ

**Non-Goals:**
- 告警/事件北向转发（Phase 11，不在本次范围）
- 录像回放北向（INVITE with time range，不在本次范围）
- 对上级的鉴权强化

## Decisions

### 1. NOTIFY 发送方式：复用 `SipMessageSender` vs 直接构造 SIP NOTIFY Request

选择：**直接构造 SIP NOTIFY Request**（参考 `CatalogQueryService.queryCatalog()` 中手动构造 SIP MESSAGE 的模式），不走 `SipMessageSender`（后者封装的是 MESSAGE 方法）。

NOTIFY 需要带 `Event: Catalog`、`Subscription-State: active` 头，与 MESSAGE 差异较大，复用代价高。在 `CatalogSubscribeHandler` 中直接用 `MessageFactory` / `HeaderFactory` 构造更清晰。

### 2. 注册后主动推送：在哪里触发

选择：在 `SipRegistrationClient.handleRegisterOk()` 中，注册成功后调用 `CatalogSubscribeHandler.pushCatalogToConfig(configId)`，通过 `InterconnectConfig` 定位对端地址构造 NOTIFY。

原因：注册成功是已知的唯一明确时机；`SipRegistrationClient` 已有 configId 上下文，可直接查库获取对端地址。

### 3. 本端相机北向 INVITE：复用 `LocalDeviceStreamService` vs 新建服务

选择：**复用 `LocalDeviceStreamService.startStream(gbDeviceId)`**，该方法已实现通过 ZLMediaKit 拉起本端 IVS1900 流并返回 `streamUrl`。在 `SipInviteService.handleIncomingInvite()` 中调用它，从 `streamUrl` 提取 RTP 端口填入 SDP。

当前 `SipInviteService` 仅处理主动发出的 INVITE（拉外域流），需新增 `onIncomingInvite(event)` 方法处理入站 INVITE。

### 4. PTZ 北向路由：在 `GbtSipListener` 还是 `DeviceCommandRouter` 中增加入口

选择：在 `DeviceCommandRouter.route()` 中增加对 `DeviceControl` CmdType 的路由分支，解析 `PTZCmd`，按 `DeviceID` 判断归属（本端/外域），分别调用 `PtzService`。

原因：`DeviceCommandRouter` 已是所有 SIP MESSAGE 命令的统一路由，PTZ 加在此处保持一致性；`GbtSipListener` 只做分发，不加业务逻辑。

## Risks / Trade-offs

- **NOTIFY Dialog 关联**：标准要求 NOTIFY 在 SUBSCRIBE 的 Dialog 内发送。简化实现可在独立事务中发送（out-of-dialog NOTIFY），部分上级平台可能拒绝。MVP 先用 out-of-dialog，后续优化。
- **本端流 SDP 端口**：`LocalDeviceStreamService` 返回的是 HTTP FLV URL，需从 ZLMediaKit 额外获取 RTP 端口用于 SDP。需调整或扩展接口返回 RTP 端口信息。
- **PTZ DeviceControl XML 解析**：`PTZCmd` 字段为十六进制编码字节串，直接透传给 `PtzService` 需适配现有接口，或新增原始 PTZ CMD 透传方法。

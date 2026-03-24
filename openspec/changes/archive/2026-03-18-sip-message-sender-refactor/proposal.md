## Why

SIP 消息发送逻辑在多个类中重复实现，导致维护困难：

- `sendOk()` 在 `ConfigDownloadHandler`、`DeviceConfigHandler`、`RemoteDeviceMessageForwarder` 中各有一份完全相同的实现
- `sendResponseMessage()`（回复入站请求方的 MESSAGE）在上述三个类中逻辑完全一致，约 40 行代码各复制一份
- 主动发出 SIP MESSAGE 的逻辑（构造 From/To/Via/CallId/CSeq/Contact 等 Header）在 `RemoteDeviceMessageForwarder`、`CatalogQueryService`、`SipRegistrationClient` 中高度重复
- `extractUser(String uri)` 在 `ConfigDownloadHandler`、`DeviceConfigHandler`、`RemoteDeviceMessageForwarder`、`CatalogNotifyHandler` 中各自定义

任何一处 SIP 发送逻辑的修改（如 Content-Type 大小写、Header 字段）都需要同步修改多处，容易遗漏。

## What Changes

新建 `SipMessageSender` Spring Bean，集中封装所有 SIP MESSAGE 的发送逻辑：

1. **发 SIP 状态响应**：`sendOk(event)` — 回复 200 OK
2. **发 SIP MESSAGE 回复**：`replyMessage(event, xmlBody)` — 向入站请求的发送方回一条新 MESSAGE（用于异步响应场景）
3. **发 SIP MESSAGE 请求**：`sendMessage(target, deviceId, callId, xmlBody)` — 主动向指定对端发送 MESSAGE
4. **工具方法**：`extractUser(uri)` — 从 SIP URI 提取用户部分

各 Handler 和 Service 删除自身的重复实现，改为注入 `SipMessageSender` 调用。

`SipMessageSender` 通过 `setSipProvider()` 接收 `SipProvider`，与现有 Handler 的初始化方式保持一致，由 `SipStackManager` 统一注入。

## Capabilities

### New Capabilities
- `sip-message-sender`: 集中封装 SIP MESSAGE 发送的工具 Bean，供所有需要发送 SIP 消息的组件复用

### Modified Capabilities

## Impact

- 修改：`ConfigDownloadHandler` — 删除 `sendOk`、`sendResponseMessage`、`extractUser`，改用 `SipMessageSender`
- 修改：`DeviceConfigHandler` — 同上
- 修改：`RemoteDeviceMessageForwarder` — 删除 `sendOk`、`sendRawResponseMessage`、`extractUser`，改用 `SipMessageSender`；`sendInitiatedMessage` 改用 `SipMessageSender.sendMessage`
- 修改：`CatalogQueryService` — MESSAGE 发送部分改用 `SipMessageSender.sendMessage`
- 修改：`CatalogNotifyHandler` — 删除 `extractUserFromUri`，改用 `SipMessageSender.extractUser`
- 修改：`SipStackManager` — 新增对 `SipMessageSender` 的 `setSipProvider` 调用
- 新增：`sip/SipMessageSender.java`

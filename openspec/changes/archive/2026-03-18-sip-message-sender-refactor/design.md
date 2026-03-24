## Context

当前 SIP 消息发送逻辑分散在以下类中，存在大量重复：

| 重复方法 | 出现位置 |
|---------|---------|
| `sendOk(event)` | ConfigDownloadHandler, DeviceConfigHandler, RemoteDeviceMessageForwarder |
| `sendResponseMessage(event, xmlBody)` | ConfigDownloadHandler, DeviceConfigHandler, RemoteDeviceMessageForwarder.sendRawResponseMessage |
| 主动发 MESSAGE 的 Header 构造 | RemoteDeviceMessageForwarder.sendInitiatedMessage, CatalogQueryService.queryCatalog, SipRegistrationClient.buildKeepaliveRequest |
| `extractUser(uri)` | ConfigDownloadHandler, DeviceConfigHandler, RemoteDeviceMessageForwarder, CatalogNotifyHandler |

## Goals / Non-Goals

**Goals:**
- 新建 `SipMessageSender` Bean，集中封装 SIP MESSAGE 发送逻辑
- 消除上述所有重复实现
- 不改变任何对外行为（纯内部重构）

**Non-Goals:**
- 不重构注册（REGISTER）相关逻辑
- 不重构 SUBSCRIBE/NOTIFY 相关逻辑
- 不改变任何业务逻辑或协议行为

## Decisions

### SipMessageSender 的 API 设计

```java
@Component
public class SipMessageSender {

    // 由 SipStackManager 在 Stack 重建后注入
    public void setSipProvider(SipProvider sipProvider);

    // 1. 回复入站请求 200 OK（SIP 状态响应）
    public void sendOk(RequestEvent event);

    // 2. 向入站请求的发送方回一条新 MESSAGE（异步响应场景）
    //    目标地址从原始请求的 Via/From Header 中提取
    public void replyMessage(RequestEvent originalEvent, String xmlBody);

    // 3. 主动向指定对端发送 MESSAGE（主动发起场景）
    //    target 提供 remoteSipId/remoteIp/remotePort/remoteDomain
    public void sendMessage(InterconnectConfig target, String deviceId, String callId, String xmlBody);

    // 4. 工具方法：从 SIP URI 字符串提取用户部分
    public static String extractUser(String uri);
}
```

### 两种 MESSAGE 发送场景的区别

**replyMessage**（回复场景）：
- 目标地址从原始请求的 `Via` Header 提取（`received`/`rport` 优先）
- `To` Header 使用原始请求的 `From` 地址
- 用于 ConfigDownloadHandler、DeviceConfigHandler、RemoteDeviceMessageForwarder 的响应回复

**sendMessage**（主动发起场景）：
- 目标地址由调用方显式传入（`InterconnectConfig`）
- `To` Header 使用 `remoteSipId@remoteDomain`
- 用于 CatalogQueryService、RemoteDeviceMessageForwarder.sendInitiatedMessage、SipRegistrationClient 心跳

### SipStackManager 注入方式

`SipMessageSender` 与现有 Handler 保持一致，通过 `setSipProvider()` 接收 Provider，在 `SipStackManager.doStart()` 末尾统一调用。

### extractUser 设为 static

该方法无状态，设为 `public static` 方便 `CatalogNotifyHandler` 等无需注入整个 Bean 的场景直接调用。

## Risks / Trade-offs

- **风险低**：纯提取重构，不改变逻辑，只是移动代码
- **测试**：现有集成测试（SIP 收发）可验证行为不变

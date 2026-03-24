## Context

本平台已有完整的 SIP MESSAGE 收发基础设施：
- `SipMessageSender.sendMessage()` — 向任意目标发送 SIP MESSAGE
- `RecordQueryService` — 展示了 CompletableFuture pending map 等待响应的标准模式
- `DeviceCommandRouter` — 入站 MESSAGE 路由分发框架
- `SipRegistrationServer.sendResponse()` — 构造并发送 SIP 响应的辅助方法

三个功能的实现均可复用上述基础设施，无需引入新依赖。

## Goals / Non-Goals

**Goals:**
- 9.10 校时：REGISTER 200 OK 中加入 `Date` 头域（ISO 8601 格式，UTC）
- 9.5 DeviceInfo/DeviceStatus：平台主动发 MESSAGE 查询，CompletableFuture 等待响应（10s 超时），北向 REST 返回 JSON
- 9.3 DeviceControl：北向 REST 触发 GuardCmd/RecordCmd/TeleBoot，南向 SIP MESSAGE 发送；入站同类命令路由到本端或转发外域

**Non-Goals:**
- 不持久化 DeviceInfo/DeviceStatus 到数据库（返回实时查询结果即可）
- 不实现 DeviceControl 的响应等待（fire-and-forget，200 OK 即成功）
- 不实现 9.9/9.12/9.13 等高复杂度功能

## Decisions

**9.10 校时 — 在 `sendResponse()` 中统一加 Date 头域**

`SipRegistrationServer.sendResponse()` 是所有 200 OK 的出口，在此处统一注入 `DateHeader`，避免散落在多处。格式遵循 GB/T 28181 A.2.1：`Date: YYYY-MM-DDTHH:MM:SS`（本地时间，无时区后缀）。

**9.5 查询 — 复用 RecordQueryService 的 pending map 模式**

新建 `DeviceInfoQueryService`，内部维护 `ConcurrentHashMap<String, CompletableFuture<String>> pending`，key = `deviceId:sn`。发送 MESSAGE 后 `future.get(10, SECONDS)`，响应到达时由 `DeviceCommandRouter` 调用 `onResponse(sn, xml)` complete future。

本端设备（IVS1900）：通过 `Ivs1900CameraMappingRepository` 找到注册的 SIP ID，向其发 MESSAGE。
外域设备：通过 `RemoteDevice` 的 `sipId`/`ip`/`port` 发 MESSAGE。

**9.3 DeviceControl — 统一用 SipMessageSender 发送，不等待响应**

GuardCmd/RecordCmd/TeleBoot 均为 fire-and-forget 控制命令，设备收到后执行，无需平台等待 Response MESSAGE。REST 接口返回 `{"sent": true}`。

入站路由：`DeviceCommandRouter` 已有 DeviceControl 路由骨架，补充 GuardCmd/RecordCmd/TeleBoot 的 case 分支，转发到本端设备或外域。

**XML 构建 — 内联字符串，不新建 XML 类**

DeviceInfo/DeviceStatus/GuardCmd/RecordCmd/TeleBoot 的 XML 结构简单（5-10行），直接用字符串模板构建，与 `AlarmSubscribeService`、`CatalogQueryService` 保持一致风格，避免过度抽象。

## Risks / Trade-offs

- [DeviceInfo 查询超时] 设备离线时 10s 阻塞 → Mitigation: 使用 `CompletableFuture.get(10, SECONDS)` + 返回 504 错误
- [Date 头域时区] GB/T 28181 要求本地时间，但服务器可能运行在 UTC → Mitigation: 使用 `ZoneId.of("Asia/Shanghai")` 格式化，可通过配置覆盖
- [DeviceCommandRouter 入站 GuardCmd] 本端 IVS1900 不一定支持 GuardCmd → Mitigation: 转发失败时记录 warn 日志，不抛异常

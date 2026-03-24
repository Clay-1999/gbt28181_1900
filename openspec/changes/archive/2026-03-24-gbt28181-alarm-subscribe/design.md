## Context

GB/T 28181-2022 §9.7 定义的报警订阅机制：平台向下级发送 `Query/CmdType=Alarm` XML（封装在 SIP MESSAGE 中），下级收到后开始持续上报 `Notify/CmdType=Alarm`。这与目录订阅（`CmdType=Catalog`）采用相同模式，**不使用** RFC 3265 的 `SUBSCRIBE` SIP 方法。

当前已有：
- `AlarmNotifyHandler` 处理入站 `NOTIFY(Event: Alarm)`（已实现）
- `AlarmController.GET /api/alarms` 查询持久化告警（已实现）
- `SipMessageSender.sendMessage()` 可主动向任意对端发送 SIP MESSAGE（复用）
- `InterconnectConfig` / `Ivs1900InterconnectConfig` 提供对端连接信息（复用）

## Goals / Non-Goals

**Goals:**
- `AlarmSubscribeService`：封装向指定对端发送 `CmdType=Alarm` 订阅 MESSAGE 的逻辑，维护已订阅 key 集合（`ConcurrentHashMap.newKeySet()`），提供 `subscribeAlarm` / `unsubscribeAlarm` / `isSubscribed` 三个公共方法
- REST 端点（IVS1900）：`POST /api/ivs1900/interconnect/{id}/alarm-subscribe`（幂等切换：已订阅则取消，未订阅则发送）、`GET /api/ivs1900/interconnect/{id}/alarm-subscribe`（查询状态）
- REST 端点（外域互联）：`POST /api/interconnects/{id}/alarm-subscribe`、`GET /api/interconnects/{id}/alarm-subscribe`

**Non-Goals:**
- 使用 RFC 3265 `SUBSCRIBE` 方法（规范采用 MESSAGE/Query 模式）
- 自动续约定时器（GB/T 28181 报警订阅无 Expires 概念，订阅状态由设备侧维护）
- 订阅状态持久化到数据库（重启后由用户或启动逻辑重新触发即可）
- NOTIFY 接收逻辑改动（`AlarmNotifyHandler` 已完整实现）

## Decisions

### 1. 使用 SIP MESSAGE 而非 RFC 3265 SUBSCRIBE

GB/T 28181 §9.7 的报警订阅与目录订阅（§9.6）机制一致，均通过 SIP MESSAGE 携带 XML Query 报文实现，不使用 `SUBSCRIBE` 方法。这避免了对话（Dialog）管理复杂性，与已有 `sendMessage()` 完全兼容。

### 2. 订阅状态 key 设计

key 格式为 `"ivs1900-{id}"` 或 `"interconnect-{id}"`，与 configId 一一对应。使用内存集合（`ConcurrentHashMap.newKeySet()`），无持久化需求，重启后状态自动清零。

### 3. POST 端点幂等切换语义

`POST /{id}/alarm-subscribe` 采用切换（toggle）语义：已订阅时调用视为取消（清除本地状态），未订阅时发送订阅 MESSAGE 并记录状态。返回 `{"subscribed": boolean, "configId": id}`，便于前端同步 UI 状态。

### 4. AlarmSubscribeService 不依赖 InterconnectConfig 仓库

服务接收 `(key, sipId, ip, port, domain)` 参数，不直接依赖仓库，保持与上层控制器的松耦合。控制器负责从仓库加载配置并提取字段后传入。

### 5. 错误处理

`subscribeAlarm` 内部 catch 异常并返回 `boolean`，发送失败不抛出异常，控制器通过返回值判断是否成功，通过 `{"subscribed": false}` 告知调用方。

## Risks / Trade-offs

- **设备侧订阅超时**：部分设备可能在一段时间后停止推送告警（无续约机制）；当前实现中用户需手动重新触发 POST 端点。后续可在应用启动时自动补发订阅（`ApplicationReadyEvent` 监听器）。
- **重启后状态丢失**：内存订阅状态清零，重启后不会自动恢复订阅；影响范围有限（运维重启后手动或自动触发即可）。

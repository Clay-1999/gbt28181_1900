## 1. 数据模型扩展

- [x] 1.1 `InterconnectConfig` 实体新增 `lastHeartbeatAt` 字段（`Instant`，可空，列名 `last_heartbeat_at`）
- [x] 1.2 `InterconnectConfigResponse` DTO 新增 `lastHeartbeatAt` 字段（`Instant`）
- [x] 1.3 `InterconnectConfigController.toResponse()` 映射 `lastHeartbeatAt`

## 2. SipRegistrationClient 重构：双定时器

- [x] 2.1 新增 `renewalTasks: Map<Long, ScheduledFuture<?>>` 和 `heartbeatTasks: Map<Long, ScheduledFuture<?>>` 及 `heartbeatFailCounts: Map<Long, Integer>`，替换原 `tasks` Map
- [x] 2.2 `handleResponse()` 收到 REGISTER 200 OK 后，同时调用 `scheduleRenewal(config, renewIn)` 和 `scheduleHeartbeat(config)`，不再调用旧的 `scheduleRegister()`
- [x] 2.3 实现 `scheduleRenewal(config, delaySeconds)`：到时发送 REGISTER refresh；收到 200 OK 则重置续约定时器；收到 4xx 或超时则取消心跳定时器，调用 `handleFailure(configId)`
- [x] 2.4 实现 `scheduleHeartbeat(config)`：60s 后发送 MESSAGE Keepalive，等待 200 OK（5s 超时）；成功则重置失败计数并调度下一次心跳；失败则 `heartbeatFailCounts++`，达到阈值（3）后取消续约定时器，调用 `handleFailure(configId)`
- [x] 2.5 实现 `buildKeepaliveRequest(config, callId)`：构造 SIP MESSAGE，Content-Type: `Application/MANSCDP+xml`，Body 为 Keepalive XML（CmdType/SN/DeviceID/Status）
- [x] 2.6 `stopRegistration(configId)` 同时取消 `renewalTasks` 和 `heartbeatTasks`，清除 `heartbeatFailCounts`
- [x] 2.7 `stopAll()` 同时清理 `renewalTasks`、`heartbeatTasks`、`heartbeatFailCounts`

## 3. SipRegistrationServer 扩展：心跳接收

- [x] 3.1 `RegistrationEntry` record 新增 `lastHeartbeatAt` 字段（`Instant`，可空）
- [x] 3.2 实现 `handleMessage(RequestEvent event)`：解析 From 头取得 `remoteSipId`，查注册表，解析 XML Body 确认 `CmdType=Keepalive`，更新 `lastHeartbeatAt`，将 `InterconnectConfig.lastHeartbeatAt` 写库，回复 200 OK；未注册则回 403
- [x] 3.3 `cleanupExpiredRegistrations()` 双重扫描条件：注册到期 OR（`lastHeartbeatAt != null` AND `now - lastHeartbeatAt > 180s`），任意一个触发则移除注册并置 `upLinkStatus=OFFLINE`

## 4. GbtSipListener 扩展：MESSAGE 分发

- [x] 4.1 `processRequest()` 新增 `case Request.MESSAGE`，调用 `sipRegistrationServer.handleMessage(event)`
- [x] 4.2 `processResponse()` 新增对 MESSAGE 响应的识别：从 `callIdToConfigId` 查不到对应 configId 时静默忽略（MESSAGE 响应由心跳逻辑的 `ClientTransaction` 直接处理，不走此路径）

## 5. 前端

- [x] 5.1 `InterconnectsView.vue` 列表在「下联状态」列之后新增「最后心跳」列，值为 `formatDate(row.lastHeartbeatAt)`，null 时显示 `"-"`

## 6. 验证

- [x] 6.1 启动应用，`GET /api/interconnects` 响应包含 `lastHeartbeatAt` 字段（初始为 null）
- [x] 6.2 用 Python 脚本模拟对端，发送 REGISTER 完成认证后，每 60s 发一次 MESSAGE Keepalive，观察 `lastHeartbeatAt` 更新、`upLinkStatus` 保持 `ONLINE`
- [x] 6.3 停止发送 Keepalive 超过 180s，观察 `upLinkStatus` 变为 `OFFLINE`
- [x] 6.4 观察 Client 侧日志，确认注册成功后有心跳发送日志，心跳日志与续约日志并行出现
- [x] 6.5 模拟对端不回心跳 MESSAGE，确认连续 3 次失败后触发重新注册，`downLinkStatus` 经历 `OFFLINE` → 重注册 → `ONLINE`（若对端恢复）

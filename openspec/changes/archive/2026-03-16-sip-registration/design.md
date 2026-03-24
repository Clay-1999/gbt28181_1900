## Context

Phase 2 已完成：`SipStackManager` 在热重载后持有一个绑定了端口的 `SipProvider`，但注册的 `SipListener` 是 `NopSipListener`（丢弃所有消息）。`InterconnectConfig` 存储了对端的 IP/Port/SipId/密码，但没有连接状态字段。Phase 3 在此基础上填充真实的 SIP 消息处理逻辑。

## Goals / Non-Goals

**Goals:**
- SIP Server：接受对端 REGISTER，完成 Digest 401 挑战认证，维护内存注册表，更新 `upLinkStatus`
- SIP Client：对每条 `enabled=true` 的 `InterconnectConfig` 发起 REGISTER，Digest 应答，心跳续约，指数退避重试，更新 `downLinkStatus`
- 热重载集成：`SipStackManager.reload()` 驱动 Client 重启注册，Server 随 SipProvider 重建自动恢复

**Non-Goals:**
- 设备目录 SUBSCRIBE/NOTIFY（Phase 4）
- 媒体协商 INVITE（Phase 4）
- NAT 穿透
- GB35114 加密

## Decisions

### 1. SipListener 拆分为 Server + Dispatcher

`SipProvider` 只允许注册一个 `SipListener`。引入 `GbtSipListener` 作为统一入口，根据消息类型分发到：
- `SipRegistrationServer`（处理收到的 REGISTER request）
- 后续 Phase 4 的其他处理器

理由：单一入口符合 JAIN-SIP 设计，同时保持各功能模块解耦。

### 2. Digest 认证实现

**Server 端**：
1. 收到无凭证 REGISTER → 生成 `nonce`（UUID）→ 返回 `401 Unauthorized`（WWW-Authenticate 头）
2. 收到带 Authorization 头的 REGISTER → 取 `InterconnectConfig.password` 计算期望 HA1/HA2/response → 对比 → 通过则 200 OK，失败则 403

nonce 存内存 Map（nonce → 创建时间），30 秒过期清理。

**Client 端**：
1. 发送无凭证 REGISTER
2. 收到 401 → 解析 `WWW-Authenticate` → 用 `InterconnectConfig.password` 计算 Digest → 带 Authorization 重发
3. 收到 200 → 记录成功，调度续约

### 3. 注册表数据结构

内存 `ConcurrentHashMap<String, RegistrationEntry>`（key = remoteSipId）：
```
RegistrationEntry { contact, expires, registeredAt, interconnectConfigId }
```
独立线程每 30 秒清理过期条目并更新 `upLinkStatus=OFFLINE`。

### 4. Client 重试策略

失败时指数退避：初始 5s，最大 300s（5 分钟），最多重试 10 次后停止并标记 `downLinkStatus=ERROR`。

每次 SipStack 热重载后重置退避计数器重新开始注册。

### 5. 状态字段

`InterconnectConfig` 新增：
- `upLinkStatus`：`ONLINE` / `OFFLINE`（对端向本端注册的状态）
- `downLinkStatus`：`REGISTERING` / `ONLINE` / `OFFLINE` / `ERROR`（本端向对端注册的状态）

直接写入 DB（非内存），前端 GET 接口即可获取最新状态。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| SipStack 热重载期间 Client 发出的 REGISTER 被新 SipStack 丢弃 | reload() 先停 Client，重建 SipStack 后再启 Client |
| Digest nonce 重放攻击 | nonce 使用后标记已用（one-time），30s 过期 |
| 对端不支持 Digest 认证（明文注册） | 暂不支持，所有 REGISTER 都要求认证 |
| H2 内存模式重启后注册状态丢失 | 重启后 Client 自动重新注册，Server 等待对端重新注册；状态字段默认 OFFLINE |

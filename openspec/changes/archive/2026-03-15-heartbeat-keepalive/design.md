## Context

Phase 3 已实现 SIP 注册（`SipRegistrationClient` / `SipRegistrationServer`），其中 Client 在注册成功后通过 `scheduleRegister(config, renewIn)` 安排单次续约。该续约周期为 `expires × 2/3`（默认约 40 分钟），期间若对端静默失联，无法被及时检测。GB/T 28181-2022 要求独立的心跳 MESSAGE 机制（周期 60s）配合注册续约共同维持连接保活，任意一个失败均触发重新注册。

## Goals / Non-Goals

**Goals:**
- Client 注册成功后同时启动心跳定时器和续约定时器，两个定时器独立运行
- 任意一个失败互相取消对方，统一进入指数退避重新注册流程
- Server 接收并响应心跳 MESSAGE，更新 `lastHeartbeatAt`
- Server 的 `@Scheduled` 扫描由单一注册过期检查扩展为双重判定（注册到期 OR 心跳超时）
- `interconnect_config` 持久化 `lastHeartbeatAt`，前端可见

**Non-Goals:**
- 心跳间隔/阈值的运行时动态配置（保持编译期常量）
- 心跳报文中 `<Info>` 扩展字段的解析（仅处理 `CmdType: Keepalive`）
- 对端设备（非平台）的心跳处理（当前只处理互联平台）

## Decisions

### D1：双定时器独立 vs 单定时器串行

**选择：两个独立的 `ScheduledFuture`，分别存入不同 Map。**

`heartbeatTasks: Map<Long, ScheduledFuture<?>>` 管理心跳定时器，`renewalTasks: Map<Long, ScheduledFuture<?>>` 管理续约定时器（取代原 Phase 3 的 `tasks` Map）。

备选：单定时器轮转（先心跳后续约）——会造成两者耦合，失败判定逻辑复杂，且续约时机难以精确控制。

### D2：心跳失败计数的存储位置

**选择：`heartbeatFailCounts: Map<Long, Integer>` 存在 `SipRegistrationClient` 内存中。**

每次 MESSAGE 发出后等待 200 OK（超时 5s），超时则累加；收到 200 OK 则清零。计数达到阈值（3）时触发重注册。

### D3：续约失败后的重注册入口

**选择：复用现有 `handleFailure(configId)` 方法。**

续约失败（REGISTER refresh 收到 4xx 或超时）时，先取消心跳定时器，再调用 `handleFailure(configId)` 进入指数退避重试，与首次注册失败路径共用同一逻辑。

### D4：Server 心跳超时阈值

**选择：固定 180s（= heartbeatInterval × 3）。**

`lastHeartbeatAt` 为空时（对端不发心跳，如旧版平台），不触发超时，仅靠注册过期检查，保持向后兼容。

### D5：MESSAGE 分发位置

**选择：在 `GbtSipListener.processRequest()` 中按 method 分发，MESSAGE 转给 `SipRegistrationServer.handleMessage()`。**

`SipRegistrationServer` 已持有注册表，就近处理心跳更新，避免引入新组件。

## Risks / Trade-offs

- **心跳 UDP 丢包误判**：单次丢包即计入失败计数，3 次连续丢包触发重注册。UDP 网络抖动可能导致不必要的重注册。缓解：阈值设为 3 而非 1，生产环境建议使用 TCP。
- **`lastHeartbeatAt` 为空时的兼容性**：对端若不发心跳（旧版平台），`lastHeartbeatAt` 永远为 null，Server 不触发心跳超时检查，仅依赖注册过期。这是预期行为，无需迁移。
- **重注册期间状态抖动**：取消心跳 → `downLinkStatus=OFFLINE` → 重注册成功 → `ONLINE`，前端 10s 轮询下最多感知 10s 的短暂离线，可接受。

## Migration Plan

1. 数据库：H2 自动 DDL（`spring.jpa.hibernate.ddl-auto=update`）会自动添加 `last_heartbeat_at` 列，无需手动迁移
2. 热部署：应用重启后 `SipStackManager.startAll()` 触发重注册，新定时器自动启动
3. 回滚：删除心跳相关代码，`last_heartbeat_at` 列保留无害

## Open Questions

- 无

## Context

当前系统已实现本端 IVS1900 相机的定时同步（`Ivs1900SyncService`）和设备目录订阅响应（`CatalogSubscribeHandler`），但缺少从外域平台同步设备的能力。GB/T 28181 标准定义了两种设备目录同步方式：主动查询（本端发 Catalog Query MESSAGE）和被动接收（处理对端推送的 NOTIFY）。

现有 SIP 基础设施：`SipRegistrationClient`（下联注册）、`SipRegistrationServer`（上联注册）、`GbtSipListener`（SIP 消息分发）、`SipStackManager`（SIP 栈生命周期）。

## Goals / Non-Goals

**Goals:**
- 新增 `remote_device` 表存储外域设备
- 下联注册成功后自动触发 Catalog Query
- 处理对端推送的 NOTIFY（Event: Catalog）
- REST API 拆分为 `/api/devices/local` 和 `/api/devices/remote`
- 前端分 Tab 展示本端/外域设备

**Non-Goals:**
- 不实现设备树层级结构（ParentID 暂不处理）
- 不实现增量同步（全量 upsert）
- 不实现外域设备的 PTZ 控制转发

## Decisions

### 1. Catalog Query 发送时机
**决策**：下联注册成功（`downLinkStatus` 首次变为 ONLINE）时触发一次查询。

**理由**：注册成功意味着链路可用，此时查询成功率最高。避免定时轮询增加不必要的 SIP 流量。

**替代方案**：定时轮询（每 N 分钟查一次）→ 增加复杂度，且对端设备变化不频繁。

### 2. NOTIFY 来源识别
**决策**：从 NOTIFY 的 `From` header 提取 SIP ID，反查 `interconnect_config` 表获取 `interconnectConfigId`。

**理由**：`remote_device` 需要关联到具体互联配置，便于前端按平台分组展示。

### 3. upsert 策略
**决策**：按 `deviceId` upsert（存在则更新 name/status/syncedAt，不存在则插入）。

**理由**：对端设备 ID 在国标体系中唯一且稳定，适合作为幂等键。

### 4. SIP 工厂注入模式
**决策**：沿用 `CatalogSubscribeHandler` 的 `setSipProvider()` 模式，由 `SipStackManager` 在 SIP 栈启动后注入。

**理由**：保持与现有代码一致，避免引入新的依赖注入机制。

### 5. API 路径变更
**决策**：原 `GET /api/devices` 改为 `GET /api/devices/local`，新增 `GET /api/devices/remote`。

**理由**：语义更清晰，前端可独立轮询两个接口。

## Risks / Trade-offs

- **NOTIFY 乱序**：对端可能分批发送多个 NOTIFY（SumNum 分页），当前实现每次 NOTIFY 独立 upsert，不处理分页聚合 → 接受，实际场景设备数量少
- **API 路径变更**：`/api/devices` → `/api/devices/local` 是破坏性变更，需同步更新前端 → 前端同步修改，无外部调用方
- **Catalog Query 无响应**：对端可能不支持 Catalog Query 或超时不回复 → 静默忽略，不影响注册流程

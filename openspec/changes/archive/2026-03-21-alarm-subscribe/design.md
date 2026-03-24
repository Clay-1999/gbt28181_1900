## Context

平台已实现南向 SIP 注册、目录同步、视频点播、PTZ 控制等能力，但告警链路完全缺失。GB/T 28181-2022 标准定义了基于 `SUBSCRIBE/NOTIFY` 的告警订阅机制：平台向下级发送 `Event: Alarm` SUBSCRIBE，下级通过 NOTIFY 上报告警。当前 `GbtSipListener` 中 NOTIFY 路径只处理 `Event: Catalog`，`Event: Alarm` 的 NOTIFY 直接被忽略。

本变更需要在已有 SIP Stack 基础上增加两条新路径：① 主动发起告警 SUBSCRIBE（含续约）；② 接收并持久化 Alarm NOTIFY，同时在前端补充订阅入口和告警查看界面。

## Goals / Non-Goals

**Goals:**
- 实现向 IVS1900 和外域互联平台发起 `Event: Alarm` SUBSCRIBE（Expires=86400），并在 2/3 到期前自动续约
- 实现接收 `CmdType=Alarm` NOTIFY，解析标准告警字段并持久化
- 提供 REST 接口触发订阅和查询告警列表
- 前端互联管理、IVS1900 管理页面增加「订阅告警」按钮
- 新增告警管理页面展示告警列表

**Non-Goals:**
- 告警联动（不实现告警触发录像、布防/撤防）
- 告警推送至第三方系统（不实现 Webhook / 消息队列集成）
- 告警已读/确认状态管理（仅展示，不做工单流转）
- 北向告警转发（不将收到的告警再转发给上级平台）

## Decisions

### 决策1：AlarmSubscribeService 独立于 SipRegistrationClient

**选择**：新建 `AlarmSubscribeService`，不复用 `SipRegistrationClient` 的续约机制。

**理由**：REGISTER 续约与 SUBSCRIBE 续约在 SIP 协议层是不同的事务。SUBSCRIBE 需要在同一 dialog 内续约（带 To-tag），而 REGISTER 是无状态的。复用会导致耦合，且 SUBSCRIBE 的失败处理策略不同（无需指数退避，简单重试即可）。

**替代方案**：在 `SipRegistrationClient.handleRegisterOk` 里直接触发订阅 → 拒绝，关注点不同。

### 决策2：告警 NOTIFY 在 GbtSipListener 中按 Event 头分发

**选择**：在 `GbtSipListener.processRequest()` 的 NOTIFY 分支中，读取 `Event` 头的 event-type 字段，`Catalog` 路由到 `CatalogSubscribeHandler`，`Alarm` 路由到 `AlarmNotifyHandler`。

**理由**：与现有 MESSAGE 按 CmdType 路由的模式一致，职责清晰，无需修改 `CatalogSubscribeHandler`。

### 决策3：告警持久化使用 H2 + JPA（与现有数据层一致）

**选择**：新建 `AlarmEvent` JPA 实体，Hibernate ddl-auto:update 自动建表。

**理由**：与 `RemoteDevice`、`Ivs1900CameraMapping` 等实体保持一致，无需额外迁移脚本。字段全部可空（来自 NOTIFY 的字段不保证完整性）。

### 决策4：订阅状态仅存内存，不持久化

**选择**：`AlarmSubscribeService` 用 `ConcurrentHashMap<Long, ScheduledFuture>` 记录活跃订阅的续约定时器（key = configId），不写数据库。

**理由**：SUBSCRIBE dialog 在进程重启后失效，持久化无意义。重启后需要用户手动重新订阅（或后续版本实现自动恢复）。前端订阅按钮状态通过查询内存 Map 判断。

### 决策5：SUBSCRIBE 使用 SipMessageSender 风格独立构造（out-of-dialog）

**选择**：初始 SUBSCRIBE 为 out-of-dialog 请求，续约使用 in-dialog（携带 Call-ID、From-tag、To-tag）。

**理由**：GB/T 28181 标准 SUBSCRIBE 须携带 `Event: Alarm`、`Expires` 头，与 MESSAGE 构造逻辑差异较大，在 `AlarmSubscribeService` 内部直接用 JAIN-SIP factories 构造，参考 `CatalogSubscribeHandler.sendNotifyTo()` 的实现模式。

## Risks / Trade-offs

- **IVS1900 可能不支持 Alarm SUBSCRIBE** → 缓解：发送失败或收到非 2xx 时记录 WARN，不影响其他功能；用户可在界面看到订阅失败状态
- **NOTIFY XML 字段不完整** → 缓解：所有 `AlarmEvent` 字段设为可空，解析失败的字段填 null，不丢弃整条告警
- **进程重启后订阅丢失** → 已接受：当前版本需手动重新订阅，属于已知限制，文档中说明
- **SIP Dialog 管理复杂度** → 缓解：初期实现中，如果 200 OK 不含 To-tag，续约时仍发 out-of-dialog SUBSCRIBE（部分平台允许），降低实现复杂度

## Migration Plan

- 无数据迁移：新表由 Hibernate ddl-auto:update 自动创建
- 无 API 破坏性变更：全为新增接口
- 前端新增页面和按钮，不修改现有页面逻辑，只追加 UI 元素

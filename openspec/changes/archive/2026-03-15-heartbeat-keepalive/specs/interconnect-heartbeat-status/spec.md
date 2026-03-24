## ADDED Requirements

### Requirement: InterconnectConfig 持久化 lastHeartbeatAt
`InterconnectConfig` 实体 SHALL 新增 `lastHeartbeatAt` 字段（`Instant` 类型，可空，映射数据库列 `last_heartbeat_at`），记录 Server 最后一次收到对端心跳的时间。

#### Scenario: 新建互联配置时 lastHeartbeatAt 为空
- **WHEN** 创建新的互联配置
- **THEN** `lastHeartbeatAt` 为 null，`GET /api/interconnects` 响应中 `lastHeartbeatAt` 字段为 null

#### Scenario: 收到心跳后 lastHeartbeatAt 更新
- **WHEN** Server 收到对端 Keepalive MESSAGE 并验证通过
- **THEN** 对应 `InterconnectConfig.lastHeartbeatAt` 更新为当前时间，并持久化到数据库

### Requirement: API 响应包含 lastHeartbeatAt
`GET /api/interconnects` 响应中每条记录 SHALL 包含 `lastHeartbeatAt` 字段（ISO-8601 格式字符串，无值时为 null）。

#### Scenario: 响应包含心跳时间字段
- **WHEN** 调用 `GET /api/interconnects`
- **THEN** 每条记录包含 `lastHeartbeatAt` 字段

### Requirement: 前端展示最后心跳时间
互联管理列表 SHALL 新增「最后心跳」列，显示 `lastHeartbeatAt` 的本地化时间字符串；当值为 null 时显示 `"-"`。

#### Scenario: 有心跳时间时显示格式化时间
- **WHEN** `lastHeartbeatAt` 不为 null
- **THEN** 列显示格式化后的本地时间（与「创建时间」列格式一致）

#### Scenario: 无心跳时间时显示占位符
- **WHEN** `lastHeartbeatAt` 为 null
- **THEN** 列显示 `"-"`

## Source: interconnect-config

## Requirements

### Requirement: 互联配置 CRUD
系统 SHALL 提供完整的互联配置增删改查 REST API，每条记录代表与一个对端平台的互联关系。

**字段定义：**
- `name`：显示名称（必填）
- `remoteSipId`：对端设备 ID（必填）
- `remoteIp`：对端 SIP 地址（必填）
- `remotePort`：对端 SIP 端口（必填）
- `remoteDomain`：对端 SIP 域（必填）
- `password`：Digest 认证密码（AES 加密存储，API 返回时以 `"***"` 掩码）
- `enabled`：是否启用下联（默认 `true`；控制本端主动向对端 REGISTER）
- `upLinkEnabled`：是否启用上联（默认 `false`；控制是否接受对端 REGISTER 进来）
- `upLinkStatus`：上联状态（`ONLINE`/`OFFLINE`，只读，由 SIP 服务端维护；`upLinkEnabled=false` 时恒为 `OFFLINE` 且无意义）
- `downLinkStatus`：下联状态（`ONLINE`/`OFFLINE`/`REGISTERING`/`ERROR`，只读，由 SIP 客户端维护）
- `lastHeartbeatAt`：最后心跳时间（只读，由 SIP 服务端维护，可空）
- `createdAt`：创建时间（自动设置，不可修改）

#### Scenario: 创建互联配置
- **WHEN** 调用 `POST /api/interconnects` 携带有效请求体
- **THEN** 新记录保存，初始 `upLinkStatus=OFFLINE`，`downLinkStatus=OFFLINE`，`upLinkEnabled=false`；若 SIP 栈状态为 `RUNNING` 且 `enabled=true`，自动启动 SIP 注册任务

#### Scenario: 查询所有互联配置
- **WHEN** 调用 `GET /api/interconnects`
- **THEN** 返回所有记录，按 `createdAt` 降序排列，密码字段掩码，包含 `upLinkEnabled` 字段

#### Scenario: 查询单条互联配置
- **WHEN** 调用 `GET /api/interconnects/{id}`
- **THEN** 返回指定 ID 的配置；不存在时返回 `404 Not Found`

#### Scenario: 更新互联配置
- **WHEN** 调用 `PUT /api/interconnects/{id}` 携带有效请求体
- **THEN** 更新配置字段；若 `enabled` 由 `false` 变为 `true`，启动 SIP 注册；若由 `true` 变为 `false`，停止 SIP 注册并将 `downLinkStatus` 置为 `OFFLINE`；若 `upLinkEnabled` 由 `true` 变为 `false`，立即调用 `SipRegistrationServer.deregisterByConfigId()` 清除上联注册并将 `upLinkStatus` 置为 `OFFLINE`

#### Scenario: 删除互联配置
- **WHEN** 调用 `DELETE /api/interconnects/{id}`
- **THEN** 停止该配置对应的 SIP 注册任务，删除记录；不存在时返回 `404 Not Found`

---

### Requirement: 注册任务与配置生命周期联动
互联配置的 `enabled` 状态变更 SHALL 驱动 SIP 注册任务的启停。

#### Scenario: 启用时自动注册
- **WHEN** 新建配置 `enabled=true` 或更新配置 `enabled: false→true`，且 SIP 栈状态为 `RUNNING`
- **THEN** 立即调用 `SipRegistrationClient.startRegistration(config)` 开始注册流程

#### Scenario: 禁用时停止注册
- **WHEN** 更新配置 `enabled: true→false`
- **THEN** 调用 `SipRegistrationClient.stopRegistration(configId)`，取消所有定时器，`downLinkStatus` 置为 `OFFLINE`

#### Scenario: SIP 栈未就绪时不自动注册
- **WHEN** 新建配置 `enabled=true`，但 SIP 栈状态不为 `RUNNING`（如 `ERROR` 或 `RELOADING`）
- **THEN** 不启动注册任务，待 SIP 栈热重载完成后由 `startAll()` 统一启动

---

### Requirement: 上联启用开关（upLinkEnabled）
`upLinkEnabled` 字段 SHALL 独立控制是否接受对端 REGISTER，与下联 `enabled` 字段相互独立。

#### Scenario: 上联未启用时拒绝对端注册
- **WHEN** 对端发来 `REGISTER` 请求，对应互联配置的 `upLinkEnabled=false`
- **THEN** SIP 服务端直接返回 `403 Forbidden`，不进入 Digest 认证流程，`upLinkStatus` 保持 `OFFLINE`

#### Scenario: 上联启用时正常认证
- **WHEN** 对端发来 `REGISTER` 请求，对应互联配置的 `upLinkEnabled=true`
- **THEN** SIP 服务端执行完整 Digest 认证流程（401 挑战 → 验证 Authorization → 200 OK），注册成功后 `upLinkStatus=ONLINE`

#### Scenario: 关闭上联时立即清除注册状态
- **WHEN** 更新配置 `upLinkEnabled: true→false`
- **THEN** 立即调用 `SipRegistrationServer.deregisterByConfigId(id)`：从内存注册表移除该配置对应的条目，`upLinkStatus` 置为 `OFFLINE`

#### Scenario: 前端上联状态展示
- **WHEN** `upLinkEnabled=false`
- **THEN** 前端「上联状态」列显示「未启用」（灰色 info tag），忽略 `upLinkStatus` 值
- **WHEN** `upLinkEnabled=true`
- **THEN** 前端按 `upLinkStatus` 显示「在线」或「离线」

#### DDL 注意事项
- `up_link_enabled` 列定义需包含 `DEFAULT FALSE`（即 `columnDefinition = "boolean default false"`），确保 Hibernate `ddl-auto: update` 对已有行执行 `ALTER TABLE ADD COLUMN` 时不因 `NOT NULL` 约束失败

---

## Source: interconnect-config-service

## ADDED Requirements

### Requirement: 互联配置 CRUD

系统 SHALL 提供互联配置的完整增删改查，持久化到 `interconnect_config` 表，密码字段透明加密存储。

#### Scenario: 新增互联配置

- **WHEN** 请求 `POST /api/interconnects` 携带合法参数
- **THEN** 写入数据库，返回 201，body 含新记录完整信息（密码脱敏）

#### Scenario: 查询全部互联配置

- **WHEN** 请求 `GET /api/interconnects`
- **THEN** 返回 200，body 为数组，按 `createdAt` 倒序，密码字段均显示 `***`

#### Scenario: 查询单条互联配置

- **WHEN** 请求 `GET /api/interconnects/{id}` 且记录存在
- **THEN** 返回 200，body 含该条配置（密码脱敏）

#### Scenario: 查询不存在的互联配置

- **WHEN** 请求 `GET /api/interconnects/{id}` 且记录不存在
- **THEN** 返回 404

#### Scenario: 修改互联配置

- **WHEN** 请求 `PUT /api/interconnects/{id}` 携带合法参数
- **THEN** 更新数据库对应记录，返回 200，body 含更新后配置（密码脱敏）

#### Scenario: 删除互联配置

- **WHEN** 请求 `DELETE /api/interconnects/{id}` 且记录存在
- **THEN** 从数据库删除，返回 204

#### Scenario: 删除不存在的互联配置

- **WHEN** 请求 `DELETE /api/interconnects/{id}` 且记录不存在
- **THEN** 返回 404

---

## Source: interconnect-heartbeat-status

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

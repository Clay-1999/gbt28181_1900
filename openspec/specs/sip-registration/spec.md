## Source: sip-client-registration

## Requirements

### Requirement: 向对端发起初始 REGISTER（Digest 挑战-应答）
`SipRegistrationClient` SHALL 向 `interconnect_config` 中 `enabled=true` 的所有对端自动发起 `REGISTER` 注册，处理 Digest 挑战-应答流程，并在注册成功后将 `downLinkStatus` 置为 `ONLINE`。

#### Scenario: 发起初始 REGISTER
- **WHEN** 互联配置 `enabled=true` 且 SIP 栈状态为 `RUNNING`
- **THEN** 发送不带 `Authorization` 的 `REGISTER`，`downLinkStatus` 先置为 `REGISTERING`

#### Scenario: 处理 401 挑战
- **WHEN** 收到 `401 Unauthorized`（含 `WWW-Authenticate` 头）
- **THEN** 用对端密码计算 MD5 Digest（`HA1=MD5(remoteSipId:realm:password)`, `HA2=MD5(REGISTER:uri)`），发送带 `Authorization` 头的新 `REGISTER`

#### Scenario: 注册成功
- **WHEN** 收到 `REGISTER 200 OK`（非续约场景）
- **THEN** `downLinkStatus` 置为 `ONLINE`，重试计数清零，同时启动续约定时器（`expires×2/3`）和心跳定时器（60s）

#### Scenario: 注册被拒绝（4xx）
- **WHEN** 收到 `4xx` 响应（如 `403 Forbidden`）
- **THEN** 调用 `handleFailure(configId)` 进入指数退避重试

---

### Requirement: 指数退避重试
注册失败时，系统 SHALL 按指数退避策略重新调度注册任务，直到成功或超过最大重试次数。

#### Scenario: 指数退避计算
- **WHEN** 第 N 次失败（N 从 0 开始）
- **THEN** 延迟 `min(5 × 2^N, 300)` 秒后重试（5s、10s、20s、40s、80s、160s、300s）

#### Scenario: 超过最大重试次数
- **WHEN** 重试次数达到 10 次
- **THEN** `downLinkStatus` 置为 `ERROR`，停止重试，等待人工干预

#### Scenario: 注册超时
- **WHEN** REGISTER 事务超时（无响应）
- **THEN** 视为失败，调用 `handleFailure(configId)` 进入指数退避重试

---

### Requirement: 停止注册清理所有资源
调用 `stopRegistration(configId)` 或 `stopAll()` 时，系统 SHALL 同时取消该配置的所有定时器并清除关联状态。

#### Scenario: 停止单条配置注册
- **WHEN** 调用 `stopRegistration(configId)`
- **THEN** 取消初始注册定时器、续约定时器、心跳定时器，清除重试计数和心跳失败计数，`downLinkStatus` 置为 `OFFLINE`

#### Scenario: 停止所有注册
- **WHEN** 调用 `stopAll()`（SIP 栈热重载前）
- **THEN** 取消所有配置的全部定时器，清除所有内部映射，所有 `downLinkStatus` 置为 `OFFLINE`

---

### Requirement: 注册成功后的续约调度
注册成功（REGISTER 200 OK）后，系统 SHALL 将续约调度作为独立的续约定时器启动（存入 `renewalTasks` Map），并在 `expires × 2/3` 时发送 REGISTER refresh。续约失败（超时或 4xx）时，系统 SHALL 取消心跳定时器，将 `downLinkStatus` 置为 `OFFLINE`，并调用 `handleFailure(configId)` 进入指数退避重新注册。**初始注册成功时（非续约），还 SHALL 异步触发一次目录 NOTIFY 推送至该上级平台。**

#### Scenario: 注册成功同时启动续约定时器
- **WHEN** `SipRegistrationClient` 收到 REGISTER 200 OK（初始注册）
- **THEN** 同时启动心跳定时器（60s 周期）和续约定时器（expires×2/3 延迟），两者独立运行，**并异步调用目录 NOTIFY 推送（失败不影响注册状态）**

#### Scenario: 续约成功重置续约定时器
- **WHEN** REGISTER refresh 收到 200 OK
- **THEN** 重新调度续约定时器（新 expires×2/3），心跳定时器继续运行不受影响

#### Scenario: 续约失败触发重新注册
- **WHEN** REGISTER refresh 超时或收到 4xx 响应
- **THEN** 取消心跳定时器，`downLinkStatus` 置为 `OFFLINE`，调用 `handleFailure(configId)` 进入指数退避重新注册

---

## Source: sip-server-registration

## Requirements

### Requirement: 处理对端 REGISTER 请求（Digest 挑战-应答）
`SipRegistrationServer` SHALL 对所有入站 `REGISTER` 请求按以下顺序处理：① 查找互联配置 → ② 检查 `upLinkEnabled` → ③ Digest 认证。

#### Scenario: 未知对端发起 REGISTER
- **WHEN** `REGISTER` 请求的 From SIP ID 不在 `interconnect_config` 中
- **THEN** 返回 `403 Forbidden`，不记录注册信息

#### Scenario: 上联未启用时拒绝注册
- **WHEN** 已找到对应互联配置，但 `upLinkEnabled=false`
- **THEN** 直接返回 `403 Forbidden`，**不进入** Digest 认证流程，`upLinkStatus` 保持 `OFFLINE`

#### Scenario: 首次 REGISTER 无认证头
- **WHEN** 收到合法对端的 `REGISTER`，`upLinkEnabled=true`，但无 `Authorization` 头
- **THEN** 生成随机 nonce（一次性使用），返回 `401 Unauthorized`（`WWW-Authenticate: Digest realm="gbt28181", nonce="..."，algorithm=MD5`）

#### Scenario: 携带 Digest 认证的 REGISTER
- **WHEN** 收到带 `Authorization` 头的 `REGISTER`，且 nonce 有效（存在且未过期）、MD5 摘要正确
- **THEN** 移除 nonce（一次性），将对端注册信息写入内存注册表，`upLinkStatus` 置为 `ONLINE`，返回 `200 OK`

#### Scenario: Digest 认证失败
- **WHEN** nonce 不存在/已过期，或 MD5 摘要不匹配
- **THEN** 返回 `403 Forbidden`

---

### Requirement: nonce 生命周期管理
nonce SHALL 为一次性使用（验证通过后立即移除），且有过期时间（30s），防止重放攻击。

#### Scenario: nonce 超时清理
- **WHEN** `@Scheduled` 每 30s 扫描 nonce Map
- **THEN** 清除所有创建时间超过 30s 的 nonce

---

### Requirement: 注册注销（Expires: 0）
系统 SHALL 处理 `Expires: 0` 的 REGISTER 请求，执行注销操作。

#### Scenario: 对端注销
- **WHEN** 认证通过的 `REGISTER` 携带 `Expires: 0`
- **THEN** 移除内存注册表中对应条目，`upLinkStatus` 置为 `OFFLINE`，返回 `200 OK`

---

### Requirement: 内存注册表维护
系统 SHALL 在内存中维护注册表（`remoteSipId → RegistrationEntry`），记录：`contact`（Contact URI）、`expires`（有效期秒数）、`registeredAt`（注册时刻 epoch second）、`lastHeartbeatAt`（最后心跳时刻 Instant，可空）、`interconnectConfigId`（关联配置 ID）。

#### Scenario: 注册信息写入注册表
- **WHEN** Digest 认证通过且 `Expires > 0`
- **THEN** 以 `remoteSipId` 为键，写入或覆盖注册表条目

---

### Requirement: 按配置 ID 强制注销（deregisterByConfigId）
`SipRegistrationServer.deregisterByConfigId(configId)` SHALL 从内存注册表中移除所有 `interconnectConfigId == configId` 的条目，并将对应配置的 `upLinkStatus` 置为 `OFFLINE`。

#### Scenario: 关闭上联时强制清除注册
- **WHEN** `InterconnectConfigService.update()` 检测到 `upLinkEnabled: true→false`
- **THEN** 调用 `deregisterByConfigId(id)`，注册表条目移除，`upLinkStatus=OFFLINE`

---

### Requirement: 定期清理过期注册（双重扫描）
`@Scheduled(fixedDelay = 30_000)` SHALL 对注册表执行双重扫描，满足任意一个条件则清除注册：
- 注册到期：`now - registeredAt > expires`
- 心跳超时：`lastHeartbeatAt != null` 且 `now - lastHeartbeatAt > 180s`

#### Scenario: 注册到期清除
- **WHEN** 对端未在有效期内续约
- **THEN** 移除注册表条目，`upLinkStatus` 置为 `OFFLINE`

#### Scenario: 心跳超时清除
- **WHEN** 对端已发送过心跳，但距最后心跳时间超过 180s
- **THEN** 移除注册表条目，`upLinkStatus` 置为 `OFFLINE`

#### Scenario: 对端从未发送心跳时不触发心跳超时
- **WHEN** `lastHeartbeatAt` 为 null
- **THEN** 跳过心跳超时检查，仅依赖注册到期检查（向后兼容）

---

### Requirement: 应用关闭时重置状态
`SipRegistrationServer.shutdown()` SHALL 清空注册表和 nonce Map，并将所有 `interconnect_config.upLinkStatus` 重置为 `OFFLINE`。

---

### Requirement: 处理入站 NOTIFY 请求（按 Event 类型分发）
`GbtSipListener` SHALL 在收到 SIP NOTIFY 请求时，读取 `Event` 头的 event-type 字段，将 `Catalog` 类型路由至 `CatalogSubscribeHandler.sendCatalogNotify()`，将 `Alarm` 类型路由至 `AlarmNotifyHandler.handle()`，其他类型回复 `200 OK` 并记录 DEBUG 日志。

#### Scenario: 收到 Event:Catalog NOTIFY
- **WHEN** 收到 SIP NOTIFY，`Event` 头值为 `Catalog`（大小写不敏感）
- **THEN** 路由至 `CatalogSubscribeHandler.sendCatalogNotify(event)`，由其负责回复 200 OK

#### Scenario: 收到 Event:Alarm NOTIFY
- **WHEN** 收到 SIP NOTIFY，`Event` 头值为 `Alarm`（大小写不敏感）
- **THEN** 路由至 `AlarmNotifyHandler.handle(event)`，由其负责回复 200 OK 并持久化告警

#### Scenario: 收到未知 Event 类型 NOTIFY
- **WHEN** 收到 SIP NOTIFY，`Event` 头值不是 Catalog 或 Alarm
- **THEN** 回复 200 OK，记录 DEBUG 日志（含 event-type 值）

---

### Requirement: REGISTER 200 OK 携带 Date 头域
`SipRegistrationServer` SHALL 在所有 REGISTER `200 OK` 响应（注册成功和注销成功）中加入 `Date` 头域，值为当前北京时间（Asia/Shanghai），格式 `YYYY-MM-DDTHH:MM:SS`，符合 GB/T 28181 A.2.1 校时规范。

#### Scenario: 注册成功响应携带 Date
- **WHEN** 下级设备 Digest 认证通过，系统返回 `200 OK`（Expires > 0）
- **THEN** 响应包含 `Date: YYYY-MM-DDTHH:MM:SS` 头域，时间为北京时间

#### Scenario: 注销响应携带 Date
- **WHEN** 下级设备发送 `Expires: 0` 注销，系统返回 `200 OK`
- **THEN** 响应包含 `Date` 头域

---

## Source: sip-client-keepalive

### Requirement: 注册成功后启动心跳定时器
Client 注册成功（收到 REGISTER 200 OK）后，系统 SHALL 立即启动心跳定时器，每隔 `heartbeatInterval`（默认 60s）向对端发送一条 SIP MESSAGE 请求，内容为 GB/T 28181 Keepalive XML。

#### Scenario: 注册成功触发心跳定时器
- **WHEN** `SipRegistrationClient` 收到 REGISTER 200 OK
- **THEN** 系统为该 configId 启动心跳定时器，60s 后首次发送 MESSAGE Keepalive

#### Scenario: 心跳发送成功
- **WHEN** 对端回复 MESSAGE 200 OK
- **THEN** 系统重置该 configId 的心跳失败计数为 0，等待下一个 60s 周期

### Requirement: 心跳失败触发重新注册
连续 `heartbeatFailThreshold`（默认 3）次 MESSAGE 无 200 OK 响应，系统 SHALL 取消心跳定时器、取消续约定时器，将 `downLinkStatus` 置为 `OFFLINE`，并启动指数退避重新注册。

#### Scenario: 单次心跳超时不触发重注册
- **WHEN** 单次 MESSAGE Keepalive 超时（5s 内无 200 OK）
- **THEN** 心跳失败计数加 1，继续等待下一个 60s 周期，不触发重注册

#### Scenario: 连续三次心跳超时触发重注册
- **WHEN** 连续 3 次 MESSAGE Keepalive 均超时
- **THEN** 系统取消心跳定时器和续约定时器，`downLinkStatus` 置为 `OFFLINE`，立即启动重新注册（指数退避）

### Requirement: 重新注册成功后重置双定时器
重新注册成功（新的 REGISTER 200 OK）后，系统 SHALL 重置心跳失败计数，重新启动心跳定时器和续约定时器。

#### Scenario: 重注册后保活恢复
- **WHEN** 重新注册收到 200 OK
- **THEN** 心跳失败计数清零，心跳定时器（60s）和续约定时器（expires×2/3）重新启动，`downLinkStatus` 置为 `ONLINE`

### Requirement: stopRegistration 同时停止心跳定时器
调用 `stopRegistration(configId)` 或 `stopAll()` 时，系统 SHALL 同时取消该 configId 的心跳定时器和续约定时器。

#### Scenario: 停止注册清理心跳资源
- **WHEN** 调用 `stopRegistration(configId)`
- **THEN** 心跳定时器和续约定时器均被取消，心跳失败计数清除

---

## Source: sip-server-keepalive

### Requirement: 接收并响应 MESSAGE Keepalive
系统 SHALL 处理对端发来的 SIP MESSAGE 请求，当请求体 XML 中 `CmdType` 为 `Keepalive` 时，更新内存注册表中该 `remoteSipId` 的 `lastHeartbeatAt` 字段，并回复 200 OK。

#### Scenario: 已注册对端发送 Keepalive
- **WHEN** `GbtSipListener` 收到 MESSAGE 请求，XML 中 `CmdType=Keepalive`，且 `From` 的 SIP ID 在注册表中
- **THEN** 更新对应注册条目的 `lastHeartbeatAt` 为当前时间，回复 200 OK，将 `upLinkStatus` 持久化为 `ONLINE`

#### Scenario: 未注册对端发送 Keepalive
- **WHEN** MESSAGE 请求的 SIP ID 不在注册表中
- **THEN** 回复 403 Forbidden，不更新任何状态

### Requirement: 双重超时扫描
`@Scheduled` 每 30s 扫描注册表，对每条注册记录执行双重检查：注册到期（`now - registeredAt > expires`）**或** 心跳超时（`lastHeartbeatAt != null && now - lastHeartbeatAt > heartbeatTimeout`），任意一个条件满足则移除该注册条目并将 `upLinkStatus` 置为 `OFFLINE`。

#### Scenario: 注册到期触发 OFFLINE
- **WHEN** 距注册时间超过 `expires` 秒，且对端未重新注册
- **THEN** 移除注册表条目，`upLinkStatus` 置为 `OFFLINE`

#### Scenario: 心跳超时触发 OFFLINE
- **WHEN** 对端已注册且发送过心跳，但距最后心跳时间超过 180s
- **THEN** 移除注册表条目，`upLinkStatus` 置为 `OFFLINE`

#### Scenario: 对端不发心跳时不触发心跳超时
- **WHEN** `lastHeartbeatAt` 为 null（对端从未发送过心跳）
- **THEN** 跳过心跳超时检查，仅依赖注册到期检查

### Requirement: GbtSipListener 分发 MESSAGE 请求
`GbtSipListener.processRequest()` SHALL 识别 SIP METHOD 为 `MESSAGE` 的请求，并转发给 `SipRegistrationServer.handleMessage()`。

#### Scenario: MESSAGE 请求正确分发
- **WHEN** `GbtSipListener` 收到 SIP METHOD 为 `MESSAGE` 的请求
- **THEN** 调用 `sipRegistrationServer.handleMessage(event)`，不打印「未处理」警告

---

## Source: time-sync

## Requirements

### Requirement: 校时 Date 头域下发
系统 SHALL 在向下级设备发送 REGISTER `200 OK` 响应时，携带 `Date` 头域，格式为 `YYYY-MM-DDTHH:MM:SS`（北京时间，GB/T 28181 A.2.1 规定格式），用于下级设备同步本地时钟。

#### Scenario: 注册成功时携带 Date 头域
- **WHEN** 下级设备 REGISTER 认证通过，系统准备返回 `200 OK`
- **THEN** 响应中包含 `Date` 头域，值为当前北京时间，格式 `YYYY-MM-DDTHH:MM:SS`

#### Scenario: 注销（Expires=0）时同样携带 Date 头域
- **WHEN** 下级设备发送 `Expires: 0` 注销请求，系统返回 `200 OK`
- **THEN** 响应中包含 `Date` 头域

#### Scenario: 认证失败时不携带 Date 头域
- **WHEN** 系统返回 `401 Unauthorized` 或 `403 Forbidden`
- **THEN** 响应中不包含 `Date` 头域

## ADDED Requirements

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

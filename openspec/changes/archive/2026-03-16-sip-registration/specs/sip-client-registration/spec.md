## ADDED Requirements

### Requirement: 向对端发起 REGISTER 并维护注册状态

系统 SHALL 对所有 `enabled=true` 的 `InterconnectConfig`，在 SipStack 启动后自动发起 SIP REGISTER，处理 Digest 挑战，在 expires 到期前续约，失败时指数退避重试，并将结果同步到 `downLinkStatus`。

#### Scenario: 初始注册流程

- **WHEN** SipStack 进入 RUNNING 状态，存在 `enabled=true` 的 InterconnectConfig
- **THEN** 对每条配置发起无凭证 REGISTER（Expires 取 `InterconnectConfig` 中 expires 或默认 3600），`downLinkStatus` 更新为 `REGISTERING`

#### Scenario: Digest 挑战应答

- **WHEN** 收到对端返回的 `401 Unauthorized`，包含 WWW-Authenticate 头
- **THEN** 解析 realm/nonce，使用 `InterconnectConfig.password` 计算 Digest response，带 Authorization 头重新发送 REGISTER

#### Scenario: 注册成功

- **WHEN** 收到对端返回的 `200 OK`
- **THEN** `downLinkStatus` 更新为 `ONLINE`，调度续约任务（在 expires 的 2/3 时间点重新发起注册）

#### Scenario: 注册失败重试

- **WHEN** 收到 `403` / `404` / 超时等失败响应
- **THEN** 按指数退避（初始 5s，翻倍，最大 300s）调度重试，`downLinkStatus` 更新为 `OFFLINE`；重试超过 10 次后 `downLinkStatus` 更新为 `ERROR`，停止重试

#### Scenario: 心跳续约

- **WHEN** 距上次注册成功已过 expires × 2/3 时间
- **THEN** 自动发起新的 REGISTER 请求，续约成功后重置计时器

#### Scenario: SipStack 热重载时重新注册

- **WHEN** `SipStackManager.reload()` 完成，SipStack 变为 RUNNING
- **THEN** 取消所有进行中的注册任务，重置退避计数器，对所有 `enabled=true` 的配置重新发起注册流程

#### Scenario: 新增/启用互联配置后自动注册

- **WHEN** 新增一条 `enabled=true` 的 InterconnectConfig，或将已有配置从 disabled 改为 enabled
- **THEN** 立即对该配置发起注册流程

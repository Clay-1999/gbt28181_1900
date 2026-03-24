## ADDED Requirements

### Requirement: 接受对端 REGISTER 并完成 Digest 认证

系统 SHALL 监听 SIP REGISTER 请求，对每个来自 `InterconnectConfig` 中已配置对端的注册请求执行 RFC 3261 Digest 认证，认证通过后维护内存注册表并更新上联状态。

#### Scenario: 首次 REGISTER 触发 401 挑战

- **WHEN** 收到无 Authorization 头的 REGISTER 请求
- **THEN** 返回 `401 Unauthorized`，WWW-Authenticate 头包含 realm 和一次性 nonce（UUID），不更新注册表

#### Scenario: 带凭证的 REGISTER 认证通过

- **WHEN** 收到携带 Authorization 头的 REGISTER，且 Digest response 与 `InterconnectConfig.password` 计算结果一致
- **THEN** 返回 `200 OK`，Contact 和 Expires 写入内存注册表，`upLinkStatus` 更新为 `ONLINE`，日志记录对端 SIP ID 和注册到期时间

#### Scenario: Digest 认证失败

- **WHEN** Authorization 头中的 Digest response 与期望值不一致
- **THEN** 返回 `403 Forbidden`，不更新注册表，`upLinkStatus` 保持不变

#### Scenario: 未知对端注册

- **WHEN** 收到 REGISTER 请求，但 From 头中的 SIP ID 在 `interconnect_config` 中不存在
- **THEN** 返回 `403 Forbidden`

#### Scenario: 注册到期自动下线

- **WHEN** 注册条目超过 Expires 时间未续约
- **THEN** 从内存注册表移除该条目，`upLinkStatus` 更新为 `OFFLINE`

#### Scenario: 主动注销（Expires=0）

- **WHEN** 收到 Expires=0 的 REGISTER（注销请求），认证通过
- **THEN** 返回 `200 OK`，从内存注册表移除，`upLinkStatus` 更新为 `OFFLINE`

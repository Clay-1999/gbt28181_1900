## ADDED Requirements

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

## MODIFIED Requirements

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

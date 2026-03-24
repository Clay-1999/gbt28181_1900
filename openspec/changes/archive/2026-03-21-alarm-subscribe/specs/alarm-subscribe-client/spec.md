## ADDED Requirements

### Requirement: 向下级发送 Event:Alarm SUBSCRIBE
`AlarmSubscribeService` SHALL 向指定互联配置（InterconnectConfig 或 Ivs1900InterconnectConfig）的 SIP 地址发送 out-of-dialog `SUBSCRIBE` 请求，携带 `Event: Alarm`、`Expires: 86400`。

#### Scenario: 发起告警订阅成功
- **WHEN** 调用 `subscribeAlarm(configId, targetSipId, targetIp, targetPort)` 且目标可达
- **THEN** 发送 SUBSCRIBE，等待 200 OK，记录 dialog 信息（Call-ID、From-tag、To-tag），注册续约定时器（在 86400×2/3 秒后触发）

#### Scenario: 发起告警订阅失败（无响应或非 2xx）
- **WHEN** SUBSCRIBE 发出后超时（5s）或收到 4xx/5xx
- **THEN** 记录 WARN 日志，不注册续约定时器，接口返回失败

#### Scenario: 重复订阅同一配置
- **WHEN** 对已有活跃订阅的 configId 再次调用订阅
- **THEN** 取消旧续约定时器，重新发起 SUBSCRIBE，以新 dialog 替换旧记录

---

### Requirement: 告警订阅自动续约
系统 SHALL 在订阅到期前（2/3 Expires 时）自动续约，续约使用 in-dialog SUBSCRIBE（相同 Call-ID、From-tag，携带已知 To-tag）。

#### Scenario: 续约成功
- **WHEN** 续约定时器触发，发送 in-dialog SUBSCRIBE
- **THEN** 收到 200 OK 后，更新 dialog 信息，重新调度下一次续约定时器

#### Scenario: 续约失败
- **WHEN** 续约 SUBSCRIBE 超时或收到非 2xx
- **THEN** 记录 WARN 日志，清除该 configId 的订阅记录，不再续约（需用户手动重新订阅）

---

### Requirement: 查询订阅状态
系统 SHALL 提供内存中活跃告警订阅的状态查询，供 REST 接口判断某 configId 是否已订阅。

#### Scenario: 查询已订阅的配置
- **WHEN** 调用 `isSubscribed(configId)` 且内存 Map 中存在该条目
- **THEN** 返回 true

#### Scenario: 查询未订阅的配置
- **WHEN** 调用 `isSubscribed(configId)` 且内存 Map 中不存在该条目
- **THEN** 返回 false

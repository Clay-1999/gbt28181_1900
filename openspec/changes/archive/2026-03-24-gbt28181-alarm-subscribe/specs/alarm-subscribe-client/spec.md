## ADDED Requirements

### Requirement: 主动发送报警订阅 MESSAGE

系统 SHALL 能向指定下级 SIP 节点发送 `Query/CmdType=Alarm` SIP MESSAGE，以主动触发对端持续上报告警事件（符合 GB/T 28181-2022 §9.7）。

#### Scenario: 向 IVS1900 设备发送报警订阅
- **WHEN** 调用 `AlarmSubscribeService.subscribeAlarm(key, sipId, ip, port, domain)`
- **THEN** 系统通过 `SipMessageSender.sendMessage()` 向目标发送包含 `<CmdType>Alarm</CmdType>` 的 XML MESSAGE
- **AND** 将 key 记录到已订阅集合，`isSubscribed(key)` 返回 true

#### Scenario: 发送失败不抛出异常
- **WHEN** `SipMessageSender.sendMessage()` 抛出异常
- **THEN** `subscribeAlarm` 捕获异常，返回 `false`
- **AND** key 不被加入已订阅集合

### Requirement: 取消报警订阅

系统 SHALL 能清除本地订阅状态，表示不再跟踪该对端的订阅关系。

#### Scenario: 取消订阅
- **WHEN** 调用 `AlarmSubscribeService.unsubscribeAlarm(key)`
- **THEN** key 从已订阅集合中移除，`isSubscribed(key)` 返回 false

### Requirement: REST 接口 — IVS1900 报警订阅

系统 SHALL 提供 REST 接口，允许通过 HTTP 触发向 IVS1900 互联配置对应设备发送/取消报警订阅。

#### Scenario: POST 触发订阅（未订阅状态）
- **WHEN** 客户端发送 `POST /api/ivs1900/interconnect/{id}/alarm-subscribe`，且 id 对应的 key 当前未订阅
- **THEN** 系统调用 `AlarmSubscribeService.subscribeAlarm(...)` 发送订阅 MESSAGE
- **AND** 返回 `{"subscribed": true, "configId": id}`（HTTP 200）

#### Scenario: POST 切换取消订阅（已订阅状态）
- **WHEN** 客户端发送 `POST /api/ivs1900/interconnect/{id}/alarm-subscribe`，且 id 对应的 key 当前已订阅
- **THEN** 系统调用 `AlarmSubscribeService.unsubscribeAlarm(key)`
- **AND** 返回 `{"subscribed": false, "configId": id}`（HTTP 200）

#### Scenario: GET 查询订阅状态
- **WHEN** 客户端发送 `GET /api/ivs1900/interconnect/{id}/alarm-subscribe`
- **THEN** 系统返回 `{"subscribed": boolean}`（HTTP 200）

#### Scenario: 配置不存在
- **WHEN** 客户端传入不存在的 id
- **THEN** 系统返回 HTTP 404

### Requirement: REST 接口 — 外域互联平台报警订阅

系统 SHALL 提供 REST 接口，允许通过 HTTP 触发向外域互联配置对应平台发送/取消报警订阅。

#### Scenario: POST 触发订阅（未订阅状态）
- **WHEN** 客户端发送 `POST /api/interconnects/{id}/alarm-subscribe`，且 id 对应的 key 当前未订阅
- **THEN** 系统调用 `AlarmSubscribeService.subscribeAlarm(...)` 向 `InterconnectConfig` 对端发送订阅 MESSAGE
- **AND** 返回 `{"subscribed": true, "configId": id}`（HTTP 200）

#### Scenario: POST 切换取消订阅（已订阅状态）
- **WHEN** 客户端发送 `POST /api/interconnects/{id}/alarm-subscribe`，且 id 对应的 key 当前已订阅
- **THEN** 系统调用 `AlarmSubscribeService.unsubscribeAlarm(key)`
- **AND** 返回 `{"subscribed": false, "configId": id}`（HTTP 200）

#### Scenario: GET 查询订阅状态
- **WHEN** 客户端发送 `GET /api/interconnects/{id}/alarm-subscribe`
- **THEN** 系统返回 `{"subscribed": boolean}`（HTTP 200）

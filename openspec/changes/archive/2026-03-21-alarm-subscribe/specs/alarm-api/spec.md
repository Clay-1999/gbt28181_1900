## ADDED Requirements

### Requirement: 触发互联配置告警订阅
系统 SHALL 提供 `POST /api/interconnects/{id}/alarm-subscribe` 接口，对指定外域互联配置发起告警订阅。

#### Scenario: 订阅成功
- **WHEN** POST `/api/interconnects/{id}/alarm-subscribe`，且 id 对应的配置存在且 SIP Stack 已就绪
- **THEN** 调用 `AlarmSubscribeService.subscribeAlarm()`，返回 `{"subscribed": true, "configId": id}`

#### Scenario: 配置不存在
- **WHEN** POST `/api/interconnects/{id}/alarm-subscribe`，id 不存在
- **THEN** 返回 404

#### Scenario: 订阅失败（网络/SIP 错误）
- **WHEN** `AlarmSubscribeService.subscribeAlarm()` 返回失败
- **THEN** 返回 `{"subscribed": false, "error": "..."}`，HTTP 200

---

### Requirement: 触发 IVS1900 互联配置告警订阅
系统 SHALL 提供 `POST /api/ivs1900/interconnect/{id}/alarm-subscribe` 接口，对指定 IVS1900 互联配置发起告警订阅。

#### Scenario: 订阅成功
- **WHEN** POST `/api/ivs1900/interconnect/{id}/alarm-subscribe`，配置存在且 SIP Stack 已就绪
- **THEN** 调用 `AlarmSubscribeService.subscribeAlarm()`，返回 `{"subscribed": true, "configId": id}`

#### Scenario: 配置不存在
- **WHEN** POST `/api/ivs1900/interconnect/{id}/alarm-subscribe`，id 不存在
- **THEN** 返回 404

---

### Requirement: 查询告警列表
系统 SHALL 提供 `GET /api/alarms` 接口，返回告警记录列表，支持分页和过滤。

#### Scenario: 查询全部告警
- **WHEN** GET `/api/alarms`（无参数）
- **THEN** 返回所有告警，按 `receivedAt` 降序，最多返回 100 条，JSON 数组

#### Scenario: 按 deviceId 过滤
- **WHEN** GET `/api/alarms?deviceId=64010400001320000001`
- **THEN** 只返回该设备的告警记录

#### Scenario: 分页查询
- **WHEN** GET `/api/alarms?page=0&size=20`
- **THEN** 返回第 page 页的 size 条记录，响应体包含 `total`、`items` 字段

---

### Requirement: 查询订阅状态
系统 SHALL 提供 `GET /api/interconnects/{id}/alarm-subscribe` 和 `GET /api/ivs1900/interconnect/{id}/alarm-subscribe` 接口，返回当前订阅状态。

#### Scenario: 查询已订阅
- **WHEN** GET 订阅状态接口，且该 configId 已有活跃订阅
- **THEN** 返回 `{"subscribed": true}`

#### Scenario: 查询未订阅
- **WHEN** GET 订阅状态接口，且该 configId 无活跃订阅
- **THEN** 返回 `{"subscribed": false}`

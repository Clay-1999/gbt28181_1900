## Source: alarm-api

## Requirements

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

---

## Source: alarm-notify-receive

## Requirements

### Requirement: 接收 Event:Alarm NOTIFY 并持久化
`AlarmNotifyHandler` SHALL 处理 `Event: Alarm` 的 SIP NOTIFY 请求，回复 200 OK，解析消息体 XML，将告警记录写入 `alarm_event` 表。

#### Scenario: 收到合法 Alarm NOTIFY
- **WHEN** 收到 SIP NOTIFY，Event 头为 `Alarm`，消息体为有效的 GB/T 28181 告警 XML
- **THEN** 回复 200 OK，解析 DeviceID、AlarmPriority、AlarmMethod、AlarmType、AlarmDescription、AlarmTime、Longitude、Latitude，写入 `alarm_event` 表，记录 INFO 日志

#### Scenario: 告警 XML 字段缺失或格式异常
- **WHEN** 消息体 XML 解析成功，但部分字段（如 Longitude、Latitude）缺失或格式异常
- **THEN** 回复 200 OK，缺失字段填 null，仍写入数据库，记录 DEBUG 日志

#### Scenario: 消息体为空或 XML 解析完全失败
- **WHEN** NOTIFY 消息体为空或无法解析为 XML
- **THEN** 回复 200 OK（避免对端重发），记录 WARN 日志，不写入数据库

---

### Requirement: AlarmEvent 数据模型
系统 SHALL 维护 `alarm_event` 表存储告警记录，字段包含：`id`（自增主键）、`deviceId`（告警设备 ID）、`alarmPriority`（优先级：1=一级/最高，2=二级，3=三级，4=四级/最低）、`alarmMethod`（报警方式，数字）、`alarmType`（报警类型，数字）、`alarmDescription`（告警描述）、`alarmTime`（告警发生时间，ISO 格式）、`longitude`（经度，可空）、`latitude`（纬度，可空）、`sourceIp`（来源 IP，从 Via 头提取）、`receivedAt`（平台接收时间）。

#### Scenario: 告警记录写入
- **WHEN** `AlarmNotifyHandler` 解析 NOTIFY 成功
- **THEN** 新建 `AlarmEvent` 实体，`receivedAt` 填当前时间，`sourceIp` 从请求 Via 头提取，保存到数据库

#### Scenario: 历史告警保留
- **WHEN** 同一设备多次上报告警
- **THEN** 每次均追加新记录，不覆盖历史，`id` 自增唯一

---

## Source: alarm-subscribe-client

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

---

## Source: alarm-ui

## Requirements

### Requirement: 互联管理页面显示订阅告警按钮
互联管理界面 SHALL 在每条互联配置条目的操作区显示「订阅告警」按钮，点击后发起告警订阅请求并更新按钮状态。

#### Scenario: 未订阅状态显示
- **WHEN** 页面加载，调用 `GET /api/interconnects/{id}/alarm-subscribe` 返回 `subscribed: false`
- **THEN** 按钮显示「订阅告警」（主色调），可点击

#### Scenario: 已订阅状态显示
- **WHEN** 查询接口返回 `subscribed: true`
- **THEN** 按钮显示「已订阅」（灰色/成功色），点击可重新订阅

#### Scenario: 点击订阅
- **WHEN** 用户点击「订阅告警」按钮
- **THEN** 调用 `POST /api/interconnects/{id}/alarm-subscribe`，成功后按钮变为「已订阅」，显示 Element Plus success 消息；失败时显示 error 消息

---

### Requirement: IVS1900 互联管理页面显示订阅告警按钮
IVS1900 互联配置界面 SHALL 在每条配置条目的操作区显示「订阅告警」按钮，交互逻辑与互联管理页面一致。

#### Scenario: 点击订阅 IVS1900
- **WHEN** 用户点击「订阅告警」按钮
- **THEN** 调用 `POST /api/ivs1900/interconnect/{id}/alarm-subscribe`，成功后更新状态

---

### Requirement: 告警管理页面（AlarmView.vue）
系统 SHALL 提供 `/alarms` 路由页面，展示告警列表，支持按设备 ID 过滤和刷新。

#### Scenario: 页面加载展示告警列表
- **WHEN** 用户导航到 `/alarms`
- **THEN** 调用 `GET /api/alarms`，以 Element Plus Table 展示：DeviceID、告警类型、告警描述、优先级（1-4 映射为文字）、发生时间、来源 IP；按 receivedAt 倒序

#### Scenario: 按设备 ID 过滤
- **WHEN** 用户在过滤输入框输入设备 ID 并点击查询
- **THEN** 调用 `GET /api/alarms?deviceId=...`，表格更新

#### Scenario: 刷新告警
- **WHEN** 用户点击刷新按钮
- **THEN** 重新调用查询接口，更新表格数据

---

### Requirement: 导航菜单增加告警管理入口
应用 SHALL 在导航菜单中增加「告警管理」菜单项，链接到 `/alarms`。

#### Scenario: 菜单项显示
- **WHEN** 用户访问应用任意页面
- **THEN** 导航栏中可见「告警管理」入口，点击跳转到 `/alarms`

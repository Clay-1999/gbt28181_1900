## MODIFIED Requirements

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

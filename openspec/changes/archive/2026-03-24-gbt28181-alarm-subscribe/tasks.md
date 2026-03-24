## 1. AlarmSubscribeService（新建）

- [x] 1.1 新建 `AlarmSubscribeService.java`（`sip/` 包），注入 `SipMessageSender`，实现 `subscribeAlarm(key, sipId, ip, port, domain)` 方法：构建 `<Query><CmdType>Alarm</CmdType><SN>...</SN><DeviceID>...</DeviceID></Query>` XML，通过 `SipMessageSender.sendMessage()` 发送，成功则将 key 加入 `ConcurrentHashMap.newKeySet()` 集合，返回 boolean
- [x] 1.2 实现 `unsubscribeAlarm(key)` 方法：从集合中移除 key，记录日志
- [x] 1.3 实现 `isSubscribed(key)` 方法：返回集合中是否包含 key
- [ ] 1.4 （可选增强）在 `ApplicationReadyEvent` 监听器中，遍历所有 enabled 的互联配置，自动补发报警订阅（避免重启后状态丢失）

## 2. REST 端点 — IVS1900 互联配置报警订阅

- [x] 2.1 在 `Ivs1900InterconnectController` 中新增 `POST /api/ivs1900/interconnect/{id}/alarm-subscribe`：从 service 加载 `Ivs1900InterconnectConfig`，构造 key=`"ivs1900-{id}"`，已订阅则调用 `unsubscribeAlarm` 并返回 `{"subscribed":false}`，未订阅则调用 `subscribeAlarm` 并返回 `{"subscribed":boolean, "configId":id}`
- [x] 2.2 在 `Ivs1900InterconnectController` 中新增 `GET /api/ivs1900/interconnect/{id}/alarm-subscribe`：返回 `{"subscribed": isSubscribed("ivs1900-{id}")}`
- [x] 2.3 配置不存在时返回 HTTP 404（确认 service.findById 在 id 不存在时抛出 404 异常，或在 controller 中显式处理）

## 3. REST 端点 — 外域互联平台报警订阅

- [x] 3.1 确认是否存在外域互联配置的控制器（`InterconnectConfigController` 或类似）；若不存在则新建，若已存在则在其中新增端点
- [x] 3.2 新增 `POST /api/interconnects/{id}/alarm-subscribe`：从 `InterconnectConfigRepository` 加载配置，构造 key=`"interconnect-{id}"`，toggle 语义同 IVS1900 端点，返回 `{"subscribed":boolean, "configId":id}`
- [x] 3.3 新增 `GET /api/interconnects/{id}/alarm-subscribe`：返回 `{"subscribed": isSubscribed("interconnect-{id}")}`
- [x] 3.4 配置不存在时返回 HTTP 404

## 4. 集成验证

- [ ] 4.1 启动应用，向 IVS1900 设备发送 `POST /api/ivs1900/interconnect/{id}/alarm-subscribe`，确认日志出现 `发送报警订阅 MESSAGE` 且 `GET` 返回 `{"subscribed":true}`
- [ ] 4.2 再次 POST 同一端点，确认返回 `{"subscribed":false}`（toggle 取消）
- [ ] 4.3 触发下级设备告警事件，确认 `AlarmNotifyHandler` 接收到 NOTIFY 并持久化，`GET /api/alarms` 能查询到新告警记录

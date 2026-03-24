## 1. 数据模型

- [x] 1.1 新建 `AlarmEvent` JPA 实体（字段：id、deviceId、alarmPriority、alarmMethod、alarmType、alarmDescription、alarmTime、longitude、latitude、sourceIp、receivedAt），表名 `alarm_event`
- [x] 1.2 新建 `AlarmEventRepository`（JpaRepository），增加 `findByDeviceId`、`findAllByOrderByReceivedAtDesc` 查询方法

## 2. 告警 NOTIFY 接收

- [x] 2.1 新建 `AlarmNotifyHandler`：`handle(RequestEvent event)` 方法，先回 `200 OK`，再解析消息体 XML（提取 DeviceID / AlarmPriority / AlarmMethod / AlarmType / AlarmDescription / AlarmTime / Longitude / Latitude），sourceIp 从 Via 头提取，写入 `AlarmEventRepository`
- [x] 2.2 修改 `GbtSipListener.processRequest()` NOTIFY 分支：读取 `Event` 头，`Alarm`（大小写不敏感）路由至 `alarmNotifyHandler.handle(event)`，`Catalog` 保持原有路由，其他类型回 200 OK + DEBUG 日志
- [x] 2.3 修改 `SipStackManager.doStart()` 注入 `AlarmNotifyHandler`（如需 `setSipProvider`）

## 3. 告警订阅客户端

- [x] 3.1 新建 `AlarmSubscribeService`（`@Component`），内部维护 `ConcurrentHashMap<Long, AlarmSubscriptionEntry>`（记录 configId → {callId, fromTag, toTag, scheduledFuture}），注入 `SipProvider` + JAIN-SIP factories（通过 `setSipProvider` 方式，与 `CatalogSubscribeHandler` 一致）
- [x] 3.2 实现 `subscribeAlarm(Long configId, String targetSipId, String targetIp, int targetPort)`：构造 out-of-dialog SUBSCRIBE（`Event: Alarm`、`Expires: 86400`），发送并等待 200 OK（5s 超时），记录 dialog 信息，调度续约定时器（86400×2/3 秒后执行）
- [x] 3.3 实现续约逻辑 `renewSubscription(Long configId)`：用 in-dialog SUBSCRIBE 续约（相同 Call-ID/From-tag，携带 To-tag），成功后重调度，失败后清除记录
- [x] 3.4 实现 `isSubscribed(Long configId)` 查询方法
- [x] 3.5 修改 `SipStackManager.doStart()` 注入 `AlarmSubscribeService.setSipProvider()`

## 4. REST 接口

- [x] 4.1 新建 `AlarmEventResponse` DTO（与 `AlarmEvent` 字段对应，`receivedAt` 格式为 ISO-8601 字符串）
- [x] 4.2 新建 `AlarmController`（`@RestController`, `@RequestMapping("/api/alarms")`）：`GET /api/alarms`（支持可选参数 `deviceId`、`page`（默认0）、`size`（默认100，上限100）），返回 `{"total": N, "items": [...]}`
- [x] 4.3 修改 `InterconnectConfigController`：新增 `POST /{id}/alarm-subscribe`（调用 `AlarmSubscribeService.subscribeAlarm()`，返回 `{"subscribed": true/false}`）和 `GET /{id}/alarm-subscribe`（返回 `{"subscribed": isSubscribed}`）
- [x] 4.4 修改 `Ivs1900InterconnectController`：同上，新增 `POST /{id}/alarm-subscribe` 和 `GET /{id}/alarm-subscribe`

## 5. 前端告警管理页面

- [x] 5.1 新建 `frontend/src/views/AlarmView.vue`：Element Plus Table 展示告警列表（DeviceID、告警类型、告警描述、优先级文字、发生时间、来源 IP），顶部有 deviceId 输入过滤框和刷新按钮，页面加载时调用 `GET /api/alarms`
- [x] 5.2 修改 `frontend/src/router/index.js`：注册 `/alarms` 路由，组件为 `AlarmView.vue`
- [x] 5.3 修改 `frontend/src/App.vue`：导航菜单增加「告警管理」菜单项，链接到 `/alarms`

## 6. 前端订阅按钮

- [x] 6.1 修改互联管理界面（找到对应的 View 文件，查看现有互联配置列表展示逻辑）：每行操作列增加「订阅告警」按钮，点击调用 `POST /api/interconnects/{id}/alarm-subscribe`；页面加载时调用 `GET /api/interconnects/{id}/alarm-subscribe` 初始化按钮状态（已订阅显示「已订阅」）
- [x] 6.2 修改 IVS1900 互联管理界面：同上，使用 `/api/ivs1900/interconnect/{id}/alarm-subscribe` 接口

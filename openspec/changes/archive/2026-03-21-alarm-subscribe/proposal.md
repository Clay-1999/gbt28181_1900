## Why

当前平台南向已支持与 IVS1900 及外域平台的 SIP 注册与目录同步，但尚未实现**告警订阅与接收**能力。下级设备上报的运动侦测、视频遮挡、设备离线等告警无法被平台感知，也无法在界面展示，运维人员无法及时响应安全事件。

## What Changes

- **新增** SIP SUBSCRIBE 告警订阅服务：向 IVS1900 及外域互联平台发送 `Event: Alarm` SUBSCRIBE 请求（Expires=86400），并在到期前自动续约
- **新增** SIP NOTIFY 告警接收处理器：解析下级上报的 `CmdType=Alarm` NOTIFY 消息，持久化告警记录
- **新增** `alarm_event` 数据库表及对应实体/仓库
- **新增** REST 接口：
  - `POST /api/interconnects/{id}/alarm-subscribe` — 对外域互联配置发起告警订阅
  - `POST /api/ivs1900/interconnect/{id}/alarm-subscribe` — 对 IVS1900 互联配置发起告警订阅
  - `GET /api/alarms` — 查询告警列表（支持 deviceId 过滤、分页）
- **新增** 前端告警管理页面 `AlarmView.vue`（路由 `/alarms`）
- **修改** 互联管理界面，增加「订阅告警」按钮
- **修改** IVS1900 互联管理界面，增加「订阅告警」按钮

## Capabilities

### New Capabilities

- `alarm-subscribe-client`: 向下级（IVS1900 / 外域平台）发送 `Event: Alarm` SUBSCRIBE 请求，Expires=86400，并在 2/3 到期时自动续约；提供 REST 触发接口
- `alarm-notify-receive`: 接收下级 SIP NOTIFY（CmdType=Alarm），解析告警字段并持久化到 `alarm_event` 表
- `alarm-api`: REST 接口：订阅触发 + 告警列表查询（分页、按 deviceId 过滤）
- `alarm-ui`: 前端告警管理页面（AlarmView.vue）+ 互联管理/IVS1900 管理页面订阅按钮

### Modified Capabilities

- `sip-server-registration`: 在 NOTIFY 处理路径中增加对 `Event: Alarm` 的分发支持（现有实现只处理 Catalog NOTIFY）

## Impact

- **后端新增文件**：
  - `domain/entity/AlarmEvent.java`
  - `domain/repository/AlarmEventRepository.java`
  - `sip/AlarmSubscribeService.java`（发送 SUBSCRIBE，管理续约定时器）
  - `sip/AlarmNotifyHandler.java`（接收 NOTIFY，解析入库）
  - `api/controller/AlarmController.java`
  - `api/dto/AlarmEventResponse.java`
- **后端修改文件**：
  - `sip/GbtSipListener.java`（NOTIFY 分发路由增加 Alarm）
  - `sip/SipStackManager.java`（注入 AlarmSubscribeService/AlarmNotifyHandler）
  - `api/controller/InterconnectConfigController.java`（新增订阅接口）
  - `api/controller/Ivs1900InterconnectController.java`（新增订阅接口）
- **前端新增文件**：`frontend/src/views/AlarmView.vue`
- **前端修改文件**：
  - `frontend/src/views/DevicesView.vue` 或互联管理视图（订阅按钮）
  - `frontend/src/router/index.js`（/alarms 路由）
  - `frontend/src/App.vue`（导航菜单）
- **数据库**：新增 `alarm_event` 表（H2 ddl-auto:update 自动建表）
- **依赖**：无新增外部依赖

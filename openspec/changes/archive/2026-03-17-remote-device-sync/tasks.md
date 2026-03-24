## 1. 实体与持久化

- [x] 1.1 新建 `domain/entity/RemoteDevice.java`：字段 `id`、`deviceId`（唯一）、`name`、`status`、`interconnectConfigId`、`syncedAt`
- [x] 1.2 新建 `domain/repository/RemoteDeviceRepository.java`：继承 `JpaRepository`，添加 `findByInterconnectConfigId(Long id)` 和 `findByDeviceId(String deviceId)`

## 2. SIP：主动 Catalog Query

- [x] 2.1 新建 `sip/CatalogQueryService.java`：`setSipProvider()`、`setSipFactories()` 注入方法，`queryCatalog(InterconnectConfig config)` 构造并发送 SIP MESSAGE（CmdType=Catalog）
- [x] 2.2 修改 `sip/SipStackManager.java`：注入 `CatalogQueryService`，在 SIP 栈启动后调用 `catalogQueryService.setSipProvider()` 和工厂注入
- [x] 2.3 修改 `sip/SipRegistrationClient.java`：注入 `CatalogQueryService`，在 `downLinkStatus` 首次变为 ONLINE 时调用 `catalogQueryService.queryCatalog(config)`
- [x] 2.4 修改 `sip/SipRegistrationServer.java`：注入 `CatalogQueryService`，在上联注册成功（`upLinkStatus` 首次变为 ONLINE）时调用 `catalogQueryService.queryCatalog(config)`

## 3. SIP：被动接收 NOTIFY

- [x] 3.1 新建 `sip/CatalogNotifyHandler.java`：`handleNotify(RequestEvent event)`，解析 XML `<DeviceList>`，按 `deviceId` upsert 到 `remote_device`，回复 200 OK
- [x] 3.2 修改 `sip/GbtSipListener.java`：注入 `CatalogNotifyHandler`，在 `processRequest()` 中新增 `NOTIFY` 分发

## 4. REST API

- [x] 4.1 新建 `api/dto/RemoteDeviceResponse.java`：字段 `deviceId`、`name`、`status`、`interconnectName`、`syncedAt`
- [x] 4.2 修改 `api/controller/DeviceController.java`：原 `GET /api/devices` 改为 `GET /api/devices/local`；新增 `GET /api/devices/remote`，注入 `RemoteDeviceRepository` 和 `InterconnectConfigRepository`

## 5. 前端

- [x] 5.1 修改 `frontend/src/views/DevicesView.vue`：改为 `el-tabs` 布局，「本端设备」Tab 调用 `/api/devices/local`，「外域设备」Tab 调用 `/api/devices/remote`，外域设备表格增加「所属互联平台」列

## 6. 验证

- [x] 6.1 编译通过：`mvn compile -q`
- [x] 6.2 启动应用，`GET /api/devices/local` 返回本端相机列表（含 gbDeviceId、status）
- [x] 6.3 `GET /api/devices/remote` 返回空数组（无外域设备时）
- [ ] 6.4 前端设备列表页显示两个 Tab，本端 Tab 有数据，外域 Tab 显示空状态
- [x] 6.5 用 Python 脚本模拟真实 SIP 对端，验证 Catalog Query 流程：
  1. 模拟端完成 SIP 注册（向本端发送 REGISTER，完成 Digest 认证）
  2. 本端触发 Catalog Query（向模拟端发送 SIP MESSAGE，CmdType=Catalog）
  3. 模拟端收到 Query 后，向本端发送 NOTIFY（Event: Catalog），携带 2-3 个模拟设备
  4. 确认 `remote_device` 表写入数据，`GET /api/devices/remote` 返回正确设备列表
- [x] 6.6 用 Python 脚本模拟真实 SIP 对端，验证下联注册触发 Catalog Query 流程：
  1. 本端向模拟端（Python SIP 服务端）发送 REGISTER，完成 Digest 认证
  2. 本端注册成功后触发 Catalog Query（MESSAGE），模拟端回复 200 OK
  3. 模拟端发送 NOTIFY（Event: Catalog），携带 2-3 个模拟设备
  4. 确认 `remote_device` 表写入数据，`GET /api/devices/remote` 返回正确设备列表
- [x] 6.6 确认下联注册成功后日志中出现 Catalog Query 发送记录
- [x] 6.7 确认上联注册成功后日志中出现 Catalog Query 发送记录
- [x] 6.8 前端外域设备 Tab 显示正确的设备名称、国标 ID、在线状态、所属互联平台

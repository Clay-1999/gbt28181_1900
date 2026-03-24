## 1. IVS1900 相机同步服务

- [x] 1.1 实现 `Ivs1900SyncService`：`@Scheduled(fixedDelayString = "${ivs1900.sync-interval:60000}")` 定时任务，调用 `GET /device/deviceList/v1.0?deviceType=2` 获取相机列表
- [x] 1.2 实现在线状态同步：调用 `GET /device/channelDevInfo`，按 `cameraCode` 匹配更新 `status`（`"true"` → `ONLINE`，`"false"` → `OFFLINE`）
- [x] 1.3 实现国标 ID 生成：新相机先 `save()` 获取自增 `id`，再生成 `gb_device_id = domainCode前10位 + "132" + String.format("%07d", id)`；`domainCode` 非纯数字时兜底用本端 SIP 设备 ID 前 10 位
- [x] 1.4 在 `application.yml` 新增 `ivs1900.sync-interval: 60000` 配置项

## 2. 设备目录订阅处理

- [x] 2.1 实现 `CatalogSubscribeHandler`：处理 `SUBSCRIBE` 请求，回复 `200 OK`，从 `ivs1900_camera_mapping` 读取全量数据，组装 GB/T 28181 `NOTIFY` XML 并发送
- [x] 2.2 在 `GbtSipListener` 中分发 `SUBSCRIBE` 请求到 `CatalogSubscribeHandler`
- [x] 2.3 处理未知 `Event` 类型：回复 `489 Bad Event`

## 3. 设备列表 REST API

- [x] 3.1 新增 `DeviceController`：`GET /api/devices` 返回 `ivs1900_camera_mapping` 全量列表（`gbDeviceId`、`name`、`status`、`syncedAt`）
- [x] 3.2 新增 `DeviceResponse` DTO

## 4. 前端设备树页

- [x] 4.1 新增 `DevicesView.vue`：表格展示本端相机列表（名称、国标 ID、在线状态、最后同步时间），30 秒轮询刷新
- [x] 4.2 在路由和导航菜单中注册「设备列表」页面

## 5. 验证

- [x] 5.1 编译通过：`mvn compile -q`
- [x] 5.2 启动应用，观察日志中出现 IVS1900 相机同步记录（相机数量、新增数量）
- [x] 5.3 调用 `GET /api/devices` 确认返回相机列表

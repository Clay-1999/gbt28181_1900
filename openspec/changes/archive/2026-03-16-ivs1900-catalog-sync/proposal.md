## Why

Phase 4 完成了 SIP 注册与心跳保活，但系统目前无法向对端发布本端设备目录。Phase 5 需要将 IVS1900 相机同步为 GB/T 28181 设备，使对端平台能通过 `SUBSCRIBE/NOTIFY Catalog` 获取本端相机列表。

## What Changes

- 新增 `Ivs1900SyncService`：每 60 秒从 IVS1900 拉取相机列表和在线状态，维护 `ivs1900_camera_mapping` 表
- 新增国标设备 ID 生成逻辑：`domainCode前10位 + "132" + id补零至7位`
- 新增 `CatalogSubscribeHandler`：处理对端 `SUBSCRIBE Catalog` 请求，回复 `200 OK` 并发送 `NOTIFY`
- 新增设备目录 REST API：`GET /api/devices`，供前端查询本端设备列表
- 新增前端设备树页：展示本端 IVS1900 相机（名称、在线状态、国标 ID）

## Capabilities

### New Capabilities

- `ivs1900-camera-sync`: IVS1900 相机列表定时同步，维护 ivs1900_camera_mapping 表，生成国标设备 ID
- `catalog-subscribe`: 响应对端 SUBSCRIBE Catalog 请求，组装 GB/T 28181 XML 发送 NOTIFY
- `device-list-api`: 设备列表 REST API，供前端查询本端设备

### Modified Capabilities

（无）

## Impact

- 新增服务类：`Ivs1900SyncService`、`CatalogSubscribeHandler`
- 新增控制器：`DeviceController`
- 修改 `GbtSipListener`：分发 SUBSCRIBE 请求到 `CatalogSubscribeHandler`
- 新增前端页面：`DevicesView.vue`
- 依赖：`Ivs1900SessionManager`（已实现）、`Ivs1900CameraMappingRepository`（已存在）

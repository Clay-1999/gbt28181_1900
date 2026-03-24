## Why

当前设备列表页只展示本端 IVS1900 相机，无法查看从互联平台同步来的外域设备。运维人员需要在同一界面统一查看本端和外域设备的在线状态，以便快速判断互联链路的设备接入情况。

## What Changes

- 新增 `remote_device` 表，存储从外域平台同步来的设备信息
- 新增主动 Catalog Query：下联或上联注册成功后，本端向对端发送 SIP MESSAGE（CmdType=Catalog）查询设备目录
- 新增被动 NOTIFY 接收：处理对端主动推送的 `NOTIFY`（Event: Catalog），解析 XML 并 upsert 到数据库
- REST API 拆分：`GET /api/devices/local`（本端）和 `GET /api/devices/remote`（外域）
- 前端设备列表页改为分 Tab 展示本端/外域设备

## Capabilities

### New Capabilities

- `remote-device-store`: 外域设备的数据模型与持久化（RemoteDevice 实体、Repository）
- `catalog-query`: 本端主动向对端发送 Catalog Query（SIP MESSAGE），触发时机为下联注册成功
- `catalog-notify-receive`: 接收并解析对端推送的 NOTIFY（Event: Catalog），upsert 外域设备
- `remote-device-api`: REST API 提供外域设备列表查询（GET /api/devices/remote）

### Modified Capabilities

- `device-list-api`: 原 `GET /api/devices` 拆分为 `/api/devices/local` 和 `/api/devices/remote`，前端 Tab 分组展示

## Impact

- 新增 JPA 实体和表：`remote_device`
- 新增 SIP 处理组件：`CatalogQueryService`、`CatalogNotifyHandler`
- 修改：`GbtSipListener`（新增 NOTIFY 分发）、`SipRegistrationClient`（注册成功触发查询）、`SipStackManager`（注入新组件）、`DeviceController`（API 路径变更）
- 前端：`DevicesView.vue` 改为 Tab 布局

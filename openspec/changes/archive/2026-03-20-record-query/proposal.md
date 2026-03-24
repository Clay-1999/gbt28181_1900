## Why

当前平台只支持实时视频播放，无法查询或回放历史录像。需要实现 GB/T 28181 的文件目录检索（RecordInfo）流程，让用户能在独立的录像页签中选择相机和时间范围查询历史录像列表。

## What Changes

- 新增后端录像查询 REST 接口：`POST /api/devices/{type}/{deviceId}/records/query`，通过 SIP MESSAGE 向设备发送 RecordInfo 查询，等待响应并返回录像列表
- 新增 SIP 报文 JAXB 类：RecordInfoQuery（请求）和 RecordInfoResponse（应答，含 RecordList）
- GbtSipListener/DeviceCommandRouter 识别 RecordInfo 响应 MESSAGE 并路由到 RecordQueryService
- 前端在主页签栏（与"本端设备"/"外域设备"同级）新增"录像查询"页签，包含：相机下拉选择（本端 + 外域设备合并列表）、时间范围选择、查询按钮、录像列表展示

## Capabilities

### New Capabilities

- `record-query`: 录像查询功能——通过 SIP RecordInfo 向本端/外域设备查询历史录像列表，REST 接口 + 独立前端页签

### Modified Capabilities

（无，不修改设备列表页签的现有行为）

## Impact

- 新增后端文件：`RecordQueryService.java`、`RecordInfoQueryXml.java`、`RecordInfoResponseXml.java`
- 修改：`DeviceCommandRouter.java`（识别 RecordInfo 响应）、`GbtSipListener.java`（路由）、`DeviceController.java`（新增端点）
- 前端修改：`DevicesView.vue`（新增"录像查询"顶级页签，查询条件区 + 录像列表区）
- 依赖：现有 `SipMessageSender`、`PtzService` 中的 pending future 机制可复用

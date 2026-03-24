## 1. SIP XML 报文类

- [x] 1.1 新建 `RecordInfoQueryXml.java`（JAXB），根元素 `<Query>`，字段：`CmdType=RecordInfo`、`SN`、`DeviceID`、`StartTime`、`EndTime`、`Type`（可选）
- [x] 1.2 新建 `RecordInfoResponseXml.java`（JAXB），根元素 `<Response>`，字段：`CmdType`、`SN`、`DeviceID`、`Name`、`SumNum`、`RecordList`（含 `Item` 列表）
- [x] 1.3 `RecordInfoResponseXml` 内嵌 `RecordItem` 类，字段：`DeviceID`、`Name`、`FilePath`、`Address`、`StartTime`、`EndTime`、`Secrecy`、`Type`、`RecorderID`、`FileSize`、`StreamNumber`

## 2. 后端：录像查询服务

- [x] 2.1 新建 `RecordQueryRequest.java`（DTO），字段：`startTime`（String）、`endTime`（String）、`type`（String，缺省 `all`）
- [x] 2.2 新建 `RecordQueryService.java`，维护 `ConcurrentHashMap<String, CompletableFuture<String>> pending`（key=`record:<sn>`），实现 `queryRecords(type, deviceId, request)` → 构建 XML → 发送 SIP MESSAGE → 10s 等待 → 解析返回 `Map<String,Object>`（含 sumNum 和 items 列表）；超时返回 null；`hasPendingSn(sn)` / `onResponse(sn, xmlBody)` 供路由调用
- [x] 2.3 在 `DeviceController` 中新增端点：`POST /api/devices/local/{gbDeviceId}/records/query` 和 `POST /api/devices/remote/{deviceId}/records/query`，调用 `RecordQueryService.queryRecords()`；超时（返回 null）时响应 HTTP 504

## 3. 后端：SIP 响应路由

- [x] 3.1 在 `GbtSipListener.processRequest` 中：在现有 pending 检查链中增加 `recordQueryService.hasPendingSn(sn)` 分支，调用 `recordQueryService.onResponse(sn, xmlBody)` 并回复 200 OK
- [x] 3.2 在 `DeviceCommandRouter` 中：识别根节点 `Response`、`CmdType=RecordInfo` 的 MESSAGE，调用 `recordQueryService.hasPendingSn(sn)` 判断是否需要路由（返回 false 则交回上层处理）

## 4. 前端：录像查询页签

- [x] 4.1 在 `el-tabs` 中新增第三个 `el-tab-pane`，`name="record"`, `label="录像查询"`，与"本端设备"/"外域设备"同级
- [x] 4.2 页签内查询条件区：`el-select`（相机选择，使用 `el-option-group` 分组，本端/外域各一组，选项显示设备名称及来源）；两个 `el-date-picker type="datetime"`（开始/结束时间，默认最近 24 小时）；"查询"按钮
- [x] 4.3 页面加载时调用 `loadAllDevices()` 合并 `/api/devices/local` 和 `/api/devices/remote`，构建相机选项列表（含 `type` 字段区分本端/外域、`id` 字段为设备 ID）
- [x] 4.4 实现 `queryRecords()` 函数：根据选中设备的 `type` 和 `id`，POST 到对应端点，设置加载状态
- [x] 4.5 录像列表用 `el-table` 展示（列：开始时间、结束时间、录像类型、文件路径），无结果时显示 `el-empty`，504 时显示 `el-alert`

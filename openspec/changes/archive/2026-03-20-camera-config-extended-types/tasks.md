## 0. 枚举 + JAXB Query 类（架构改进）

- [x] 0.1 新建 `CameraConfigType.java` 枚举，包含全部 8 种类型（VideoParamAttribute、OSDConfig、PictureMask、FrameMirror、VideoRecordPlan、VideoAlarmRecord、AlarmReport、SnapShot），每个枚举值携带 `urlSegment` 字段，提供 `fromUrlSegment(String)` 静态方法
- [x] 0.2 新建 `ConfigDownloadQuery.java` JAXB 类（`@XmlRootElement(name = "Query")`），字段：`CmdType="ConfigDownload"`、`SN`、`DeviceID`、`ConfigType`（String）
- [x] 0.3 `Ivs1900SipConfigService.queryConfig()` 中手动拼接的 ConfigDownload XML 改为使用 `ConfigDownloadQuery` + `GbXmlMapper.toXml()`；configType 参数类型改为 `CameraConfigType`，switch 改为 enum switch
- [x] 0.4 `RemoteDeviceMessageForwarder.queryConfig()` 中手动拼接的 ConfigDownload XML 改为使用 `ConfigDownloadQuery` + `GbXmlMapper.toXml()`
- [x] 0.5 `Ivs1900SipConfigService.setConfig()`、`buildDeviceConfigXml()` 的 configType 参数改为 `CameraConfigType`
- [x] 0.6 `RemoteDeviceConfigService.queryConfig()`、`setConfig()`、`parseConfigXml()`、`buildDeviceConfigXml()` 的 configType 参数改为 `CameraConfigType`
- [x] 0.7 `DeviceController` 所有配置端点从 URL segment 解析 `CameraConfigType`，传给 Service

## 1. JAXB XML 类（8 个，全注解）

- [x] 1.1 新建 `VideoRecordPlanResponse.java`：Response 根元素，含 `<VideoRecordPlan>` 子元素（`RecordMethod`、`StreamType`）
- [x] 1.2 新建 `VideoRecordPlanControl.java`：Control 根元素，含 `<VideoRecordPlan>` 子元素
- [x] 1.3 新建 `VideoAlarmRecordResponse.java`：Response 根元素，含 `<VideoAlarmRecord>` 子元素（`PreRecordTime`、`AlarmRecordTime`）
- [x] 1.4 新建 `VideoAlarmRecordControl.java`：Control 根元素，含 `<VideoAlarmRecord>` 子元素
- [x] 1.5 新建 `AlarmReportResponse.java`：Response 根元素，含 `<AlarmReport>` 子元素（`AlarmMethod`、`AlarmRecordTime`、`PreRecordTime`）
- [x] 1.6 新建 `AlarmReportControl.java`：Control 根元素，含 `<AlarmReport>` 子元素
- [x] 1.7 新建 `SnapShotResponse.java`：Response 根元素，含 `<SnapShot>` 子元素（`SnapShotInterval`、`SnapShotTimes`）
- [x] 1.8 新建 `SnapShotControl.java`：Control 根元素，含 `<SnapShot>` 子元素

## 2. Ivs1900SipConfigService 扩展

- [x] 2.1 `parseConfigXml` switch 新增 4 个 case（使用枚举）：`VideoRecordPlan`、`VideoAlarmRecord`、`AlarmReport`、`SnapShot`
- [x] 2.2 新增 `parseVideoRecordPlan`、`parseVideoAlarmRecord`、`parseAlarmReport`、`parseSnapShot` 4 个私有方法
- [x] 2.3 `buildDeviceConfigXml` switch 新增 4 个 case
- [x] 2.4 新增 `buildVideoRecordPlanXml`、`buildVideoAlarmRecordXml`、`buildAlarmReportXml`、`buildSnapShotXml` 4 个私有方法

## 3. RemoteDeviceConfigService 扩展

- [x] 3.1 `parseConfigXml` switch 新增 4 个 case，复用相同解析逻辑
- [x] 3.2 `buildDeviceConfigXml` switch 新增 4 个 case

## 4. DeviceController 新增端点

- [x] 4.1 本端设备新增 4 组 GET/PUT 端点：`/local/{gbDeviceId}/config/video-record-plan`、`video-alarm-record`、`alarm-report`、`snap-shot`，通过 `CameraConfigType.fromUrlSegment()` 解析类型
- [x] 4.2 外域设备新增 4 组 GET/PUT 端点（同上）

## 5. 前端 DevicesView.vue

- [x] 5.1 配置对话框新增"录像计划"tab（`video-record-plan`）：`recordMethod`（select）、`streamType`（select）
- [x] 5.2 新增"报警录像"tab（`video-alarm-record`）：`preRecordTime`（input-number）、`alarmRecordTime`（input-number）
- [x] 5.3 新增"报警上报"tab（`alarm-report`）：`alarmMethod`（select）、`alarmRecordTime`（input-number）、`preRecordTime`（input-number）
- [x] 5.4 新增"抓图"tab（`snap-shot`）：`snapShotInterval`（input-number）、`snapShotTimes`（input-number）
- [x] 5.5 `applyFormData` 新增 4 个 tab 的数据填充逻辑
- [x] 5.6 `resetFormData` 新增 4 个 tab 的重置逻辑
- [x] 5.7 `buildPatch` 新增 4 个 tab 的 patch 构建逻辑

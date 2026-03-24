## Why

当前相机配置界面只支持 4 种类型（VideoParamAttribute、OSDConfig、PictureMask、FrameMirror）。GB/T 28181 标准还定义了 VideoRecordPlan（录像计划）、VideoAlarmRecord（报警录像）、AlarmReport（报警上报）、SnapShot（抓图）4 种常用配置类型，运维人员需要通过界面查看和修改这些参数。

## What Changes

- 后端 `Ivs1900SipConfigService`：新增 4 种配置类型的 SIP MESSAGE 查询/下发支持（parse + build XML）
- 后端 `RemoteDeviceConfigService`：同步新增 4 种类型的外域设备配置查询/下发支持
- 后端 `DeviceController`：新增 4 种类型对应的 GET/PUT 端点（本端 + 外域各 4×2=8 个）
- 新增 JAXB XML 类：`VideoRecordPlanResponse/Control`、`VideoAlarmRecordResponse/Control`、`AlarmReportResponse/Control`、`SnapShotResponse/Control`
- 前端 `DevicesView.vue`：配置对话框新增 4 个 tab，展示对应表单

## Capabilities

### New Capabilities

- `camera-config-extended`: 4 种扩展配置类型（VideoRecordPlan、VideoAlarmRecord、AlarmReport、SnapShot）的后端查询/下发 + 前端表单

### Modified Capabilities

- `config-ui`: 配置对话框新增 4 个 tab

## Impact

- 新增 8 个 JAXB 类（4 种类型 × Response + Control）
- 修改 `Ivs1900SipConfigService`：parseConfigXml + buildDeviceConfigXml 各增加 4 个 case
- 修改 `RemoteDeviceConfigService`：同步增加 4 个 case
- 修改 `DeviceController`：新增 16 个端点（本端 + 外域各 8 个）
- 修改 `DevicesView.vue`：新增 4 个 tab + 表单

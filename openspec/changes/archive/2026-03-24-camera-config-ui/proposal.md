## Why

设备列表页只能查看设备状态，无法查看或修改相机配置参数。运维人员需要通过界面直接调整视频编码、OSD、遮挡、镜像等参数，而不必依赖设备厂商工具或手动发 SIP 消息。

## What Changes

- 新增 REST 接口：`GET/PUT /api/devices/local/{gbDeviceId}/config?type=...`（本端 IVS1900 相机）
- 新增 REST 接口：`GET/PUT /api/devices/remote/{deviceId}/config?type=...`（外域 GBT28181 设备）
- 本端 GET/PUT 直接调用 `Ivs1900DeviceConfigClient` 对应方法
- 外域 GET 发送 ConfigDownload SIP MESSAGE 并等待响应；外域 PUT 发送 DeviceConfig SIP MESSAGE 并等待响应
- 支持配置类型：`VideoParamAttribute`、`OSDConfig`、`PictureMask`、`FrameMirror`
- 前端 DevicesView.vue 每行增加"配置"按钮，点击弹出对话框，按配置类型分 tab 展示表单，支持加载和保存

## Capabilities

### New Capabilities

- `local-device-config-api`: 本端相机配置查询与下发 REST 接口
- `remote-device-config-api`: 外域设备配置查询与下发（通过 GBT28181 SIP）
- `camera-config-dialog`: 前端相机配置对话框（查看 + 修改）

### Modified Capabilities

（无现有 spec 级别行为变更）

## Impact

- `DeviceController`：新增 4 个 endpoint
- 新建 `DeviceConfigService`：封装本端配置查询/下发逻辑
- `RemoteDeviceMessageForwarder`：新增 `queryConfig` / `setConfig` 主动发起方法
- `GbtSipListener`：识别外域配置响应 MESSAGE，触发 pending future
- `frontend/src/views/DevicesView.vue`：增加配置按钮和对话框
- 无新依赖，无 API 破坏性变更

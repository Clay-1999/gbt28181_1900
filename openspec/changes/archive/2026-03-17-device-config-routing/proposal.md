## Why

当前系统能接受对端平台发来的 SIP REGISTER 并同步外域设备目录，但尚未处理对端对具体设备发出的控制/查询命令（ConfigDownload、DeviceConfig）。收到命令后必须根据目标设备的归属决定路由：本端 IVS1900 相机需翻译为 ivs1900 REST API 调用，外域平台设备需透传 GB/T 28181 SIP MESSAGE 给对应的互联对端。

## What Changes

- **新增** 设备命令路由入口：收到 SIP MESSAGE 后，按 DeviceID 三路分发
- **新增** 本端设备 ConfigDownload 处理：查询 ivs1900 相机配置参数，支持全部 12 种 ConfigType
- **新增** 本端设备 DeviceConfig 处理：下发 ivs1900 相机配置参数，支持 11 种子命令
- **新增** 外域设备命令透传：原样转发 GB/T 28181 SIP MESSAGE 给对端平台，透传应答
- **新增** 未知设备回复 404：DeviceID 不在任何表时回复 `SIP/2.0 404 Not Found`

## Capabilities

### New Capabilities

- `device-command-router`: 按 DeviceID 归属（ivs1900 本端 / 外域平台 / 未知）路由 SIP MESSAGE 命令
- `config-download-handler`: 处理 ConfigDownload 查询命令，本端设备调 ivs1900，外域设备转发
- `device-config-handler`: 处理 DeviceConfig 配置命令，本端设备调 ivs1900，外域设备转发

### Modified Capabilities

（无现有 spec 行为变更）

## Impact

- **SIP 层**：`GbtSipListener.processRequest()` 新增对 MESSAGE 中 ConfigDownload / DeviceConfig 的分发
- **新建处理类**：`DeviceCommandRouter`、`ConfigDownloadHandler`、`DeviceConfigHandler`
- **依赖**：`Ivs1900CameraRepository`（查本端设备）、`RemoteDeviceRepository`（查外域设备）、`InterconnectConfigRepository`（查对端 SIP 地址）
- **ivs1900 REST API**：`POST /device/camera/batchconfig/v1.0`（配置查询）、各配置写入接口
- **无 DB schema 变更**，无前端变更

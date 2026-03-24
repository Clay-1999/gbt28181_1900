## Why

目前系统通过私有 HTTPS REST API 直接访问 IVS1900，导致与 IVS1900 的集成强依赖其私有协议，无法复用已有的 GB/T 28181 互联框架。IVS1900 本身支持 GB/T 28181 协议作为**下级设备**向上级平台注册；改为标准协议接入后，可统一设备接入路径，彻底消除对 IVS1900 私有 HTTP API 的依赖。

## What Changes

- **新增** IVS1900 互联参数配置：存储 IVS1900 的 GB/T 28181 SIP ID（用于识别其主动 REGISTER 进来），以及向其发送 SIP MESSAGE 所需的 SIP 地址
- **IVS1900 作为下级**主动向本平台发起 REGISTER，`SipRegistrationServer` 识别其 SIP ID 并记录上联状态
- **目录同步**：IVS1900 注册成功后，本平台向其发送 `SUBSCRIBE Catalog`，触发 IVS1900 推送 Catalog Notify，`CatalogNotifyHandler` 解析写入 `ivs1900_camera_mapping`
- **设备配置命令改走 GB/T 28181**：原 `Ivs1900DeviceConfigClient`（私有 HTTP）废弃，改为通过 SIP MESSAGE（`CmdType=DeviceConfig` / `ConfigDownload`）向 IVS1900 下发配置
- **完全移除** IVS1900 私有 HTTP 集成：`Ivs1900SessionManager`、`Ivs1900HttpClient`、`Ivs1900DeviceConfigClient`、`Ivs1900SyncService`
- **新增** 前端 IVS1900 互联参数配置界面
- **移除** `application.yml` 中所有 `ivs1900.base-url`、`ivs1900.username`、`ivs1900.password` 配置项

## Capabilities

### New Capabilities

- `ivs1900-interconnect-config`: IVS1900 GB/T 28181 互联参数的存储与管理（CRUD API + 前端配置界面），存储 IVS1900 的 SIP ID 及联系地址，供识别其注册和向其发送 SIP MESSAGE 使用

### Modified Capabilities

- `ivs1900-integration`: 设备目录同步来源从 IVS1900 HTTP API 改为 GB/T 28181 Catalog Notify；IVS1900 作为下级主动 REGISTER 进来；设备配置命令改用 SIP MESSAGE
- `ivs1900-http-refactor`: IVS1900 所有私有 HTTP 客户端（SessionManager、HttpClient、DeviceConfigClient、SyncService）全部移除

## Impact

- **移除**：`Ivs1900SessionManager`、`Ivs1900HttpClient`、`Ivs1900DeviceConfigClient`、`Ivs1900SyncService`、`Ivs1900HttpConfig`（SSL RestTemplate Bean）、`Ivs1900Properties`
- **新增实体**：`Ivs1900InterconnectConfig`（仅存 SIP 参数，无 HTTP 参数）
- **修改**：`SipRegistrationServer` 扩展识别 `Ivs1900InterconnectConfig.sipId` 的 REGISTER 请求，更新上联状态
- **修改**：`CatalogNotifyHandler` 识别 IVS1900 的 Notify，写入 `ivs1900_camera_mapping`
- **修改**：设备配置 REST API 对 IVS1900 相机改用 SIP MESSAGE（`RemoteDeviceMessageForwarder` 模式）
- **前端**：IVS1900 配置页（只需 SIP 参数）；设备配置对话框 IVS1900 相机的配置走 SIP

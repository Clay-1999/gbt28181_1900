## Context

当前系统通过 IVS1900 私有 HTTPS REST API 集成 IVS1900：
- `Ivs1900SessionManager` 负责登录/保活
- `Ivs1900HttpClient` 拉取设备列表和在线状态
- `Ivs1900SyncService` 每 60s 定时调 HTTP 接口，将相机写入 `ivs1900_camera_mapping`
- `Ivs1900DeviceConfigClient` 提供设备参数查询/下发（OSD、码流等）

IVS1900 支持 GB/T 28181 协议，**作为下级设备**向上级平台注册。本平台已有完整的上级能力：
- `SipRegistrationServer`：接收下级 REGISTER，维护上联状态
- `CatalogQueryService`：主动发 SUBSCRIBE Catalog
- `CatalogNotifyHandler`：处理 Catalog Notify，写 `RemoteDevice`
- `RemoteDeviceMessageForwarder`：向下级发 SIP MESSAGE（DeviceConfig/ConfigDownload），等待响应

**正确方向**：IVS1900 主动向本平台 REGISTER → 本平台识别并记录 → 发 SUBSCRIBE Catalog → 收 Notify 写 `ivs1900_camera_mapping` → 设备配置命令走 SIP MESSAGE。

## Goals / Non-Goals

**Goals:**
- 新增 `Ivs1900InterconnectConfig` 实体，**仅存储 SIP 参数**（SIP ID、IP、端口、域、密码），用于识别 IVS1900 的 REGISTER 及向其发 SIP MESSAGE
- 提供 CRUD REST API 管理该配置
- `SipRegistrationServer` 识别 IVS1900 的 REGISTER（匹配 `sipId`），记录连接状态，触发 SUBSCRIBE Catalog
- `CatalogNotifyHandler` 识别来自 IVS1900 的 Notify，写入 `ivs1900_camera_mapping`
- 设备配置 REST API 对 IVS1900 相机改用 SIP MESSAGE（`RemoteDeviceMessageForwarder` 模式）
- 完全移除所有 IVS1900 私有 HTTP 组件
- 前端 IVS1900 配置页（仅 SIP 参数）

**Non-Goals:**
- 不支持同时配置多个 IVS1900 实例（单条配置）
- 不实现 GB/T 28181 视频点播功能变更

## Decisions

### 决策 1：IVS1900 角色为下级（被服务方），不是上级

本平台是**上级平台**，IVS1900 主动向我们 REGISTER（不是我们向 IVS1900 注册）。`SipRegistrationServer` 已有接收下级 REGISTER 的能力，只需在识别逻辑中加入 `Ivs1900InterconnectConfig.sipId` 匹配。

### 决策 2：Ivs1900InterconnectConfig 只存 SIP 参数，不含 HTTP 参数

原设计将 `httpBaseUrl`、`httpUsername`、`httpPassword` 存入此实体，是为了保留私有 HTTP 调用。正确方向是完全弃用私有 HTTP，因此实体只需 SIP 相关字段：`sipId`、`ip`、`port`、`domain`、`password`。

### 决策 3：设备配置命令走 SIP MESSAGE

原 `Ivs1900DeviceConfigClient` 通过私有 HTTP 发设备配置。改为通过 `RemoteDeviceMessageForwarder`（或新建 `Ivs1900SipConfigClient`）构造 GB/T 28181 SIP MESSAGE（`CmdType=DeviceConfig` / `ConfigDownload`），发给 IVS1900，等待其响应 MESSAGE。

复用模式参考现有 `RemoteDeviceConfigService`（已实现外域设备的 SIP 配置下发）。

### 决策 4：SipRegistrationServer 触发 SUBSCRIBE Catalog

IVS1900 REGISTER 成功后，`SipRegistrationServer` 通过 `Ivs1900InterconnectConfigRepository` 识别来源，调用 `CatalogQueryService.queryCatalog()` 触发目录同步，与外域设备的处理逻辑一致。

## Risks / Trade-offs

- **[风险] IVS1900 的 SIP 报文格式**：DeviceConfig / ConfigDownload 的 XML 结构需实测，可能与外域设备有差异。→ 缓解：先实现框架，XML 内容可按标准模板生成，实测后微调。
- **[风险] 注册成功后目录同步时延**：Notify 是被动推送，需 IVS1900 响应 SUBSCRIBE。→ 缓解：记录等待日志，超时后可重试 SUBSCRIBE。
- **[Trade-off] 完全弃用私有 HTTP**：部分 IVS1900 功能（如精细的 OSD 字体大小配置）可能 GB/T 28181 标准无法覆盖。→ 接受，本期只做标准协议部分。

## Migration Plan

1. 清理已实现的错误代码（移除 `SipRegistrationClient` 调用、HTTP 参数字段）
2. 修正实体只保留 SIP 字段
3. 修正 `SipRegistrationServer` 识别 IVS1900 REGISTER 并触发 Catalog 订阅
4. 实现 SIP MESSAGE 设备配置下发
5. 完全删除私有 HTTP 组件
6. 验证：IVS1900 REGISTER → SUBSCRIBE Catalog → Notify → 相机出现在设备列表

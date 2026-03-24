## Source: ivs1900-integration

## MODIFIED Requirements

### Requirement: 相机列表同步
系统 SHALL 通过 GB/T 28181 Catalog Notify 接收 IVS1900 推送的相机列表，**不再**通过 HTTP 定时拉取同步。`Ivs1900SyncService` 的定时 HTTP 拉取逻辑 SHALL 被完全移除。

#### Scenario: IVS1900 注册成功后触发目录订阅
- **WHEN** `SipRegistrationServer` 收到 IVS1900 的 REGISTER 请求，其 SIP ID 匹配 `Ivs1900InterconnectConfig.sipId`
- **THEN** 更新 `upLinkStatus=ONLINE`，调用 `CatalogQueryService.queryCatalog()` 向 IVS1900 发送 `SUBSCRIBE Catalog`

#### Scenario: 收到 IVS1900 Catalog Notify 写入映射表
- **WHEN** `CatalogNotifyHandler` 收到来自 IVS1900 `sipId` 的 Catalog Notify
- **THEN** 解析 XML 中的设备列表，对每个设备条目执行 upsert：新设备生成国标 ID 写入 `ivs1900_camera_mapping`，已有设备更新 `name`、`status`、`syncedAt`

#### Scenario: IVS1900 注销时更新状态
- **WHEN** `SipRegistrationServer` 收到 IVS1900 的 `Expires: 0` REGISTER（注销请求）
- **THEN** 更新 `upLinkStatus=OFFLINE`

---

### Requirement: 设备配置命令走 GB/T 28181 SIP MESSAGE
系统 SHALL 通过 GB/T 28181 SIP MESSAGE（`CmdType=DeviceConfig` / `ConfigDownload`）向 IVS1900 相机下发和查询配置，不再使用私有 HTTP API。

**配置类型映射（与外域设备相同）：**
- `VideoParamAttribute`：视频编码参数
- `OSDConfig`：OSD 叠加字幕
- `PictureMask`：遮挡区域
- `FrameMirror`：镜像/翻转

#### Scenario: 查询 IVS1900 相机配置
- **WHEN** 调用 `GET /api/devices/local/{gbDeviceId}/config?type=<configType>`，该设备属于 IVS1900
- **THEN** 通过 `SipMessageSender` 向 IVS1900 发送 `CmdType=ConfigDownload` SIP MESSAGE，等待响应（超时 10s），解析 XML 返回 JSON

#### Scenario: 下发 IVS1900 相机配置
- **WHEN** 调用 `PUT /api/devices/local/{gbDeviceId}/config?type=<configType>` 携带配置 JSON，该设备属于 IVS1900
- **THEN** 构造 `CmdType=DeviceConfig` SIP MESSAGE XML，通过 `SipMessageSender` 发送给 IVS1900，等待响应，返回 `{"success": true/false}`

## REMOVED Requirements

### Requirement: IVS1900 HTTP 定时同步
**Reason**: 相机同步改由 GB/T 28181 Catalog Notify 驱动，HTTP 拉取逻辑已无存在必要。
**Migration**: `Ivs1900SyncService` 完全删除。

### Requirement: IVS1900 私有 HTTP 设备配置
**Reason**: 设备配置命令改走 GB/T 28181 SIP MESSAGE，`Ivs1900DeviceConfigClient` 私有 HTTP 调用全部废弃。
**Migration**: 删除 `Ivs1900DeviceConfigClient`、`Ivs1900HttpClient`、`Ivs1900SessionManager`、`Ivs1900HttpConfig`。

---

## Source: ivs1900-camera-sync

## ADDED Requirements

### Requirement: IVS1900 相机列表定时同步
系统 SHALL 每 60 秒从 IVS1900 拉取相机列表和在线状态，维护 `ivs1900_camera_mapping` 表。

#### Scenario: 首次同步生成国标 ID
- **WHEN** 从 IVS1900 查询到新相机（`ivsCameraId` 不在映射表中）
- **THEN** 将相机写入 `ivs1900_camera_mapping`，生成国标设备 ID（`domainCode前10位 + "132" + id补零至7位`），`id` 取自自增主键

#### Scenario: 更新在线状态
- **WHEN** 定时同步任务执行
- **THEN** 调用 `GET /device/channelDevInfo`，解析 `isOnline` 字段（字符串 `"true"`/`"false"`），更新映射表的 `status` 字段（`ONLINE`/`OFFLINE`）

#### Scenario: 更新相机名称
- **WHEN** IVS1900 相机名称变更
- **THEN** 更新映射表的 `name` 字段

#### Scenario: IVS1900 不可达时保留已有数据
- **WHEN** 定时同步任务调用 IVS1900 接口失败
- **THEN** 记录错误日志，保留映射表中已有数据不变，不清空设备列表

---

## Source: ivs1900-interconnect-config

## ADDED Requirements

### Requirement: IVS1900 互联参数 CRUD
系统 SHALL 提供 `Ivs1900InterconnectConfig` 实体及完整的 REST CRUD API，用于存储 IVS1900 的 GB/T 28181 SIP 接入参数。

**字段定义：**
- `sipId`：IVS1900 的 GB/T 28181 设备 ID（必填），用于识别其 REGISTER 请求及向其发送 SIP MESSAGE
- `ip`：IVS1900 SIP 监听 IP（必填），用于向其发送 SIP MESSAGE
- `port`：IVS1900 SIP 端口（必填）
- `domain`：IVS1900 SIP 域（必填）
- `password`：Digest 认证密码（存储明文，API 返回时掩码为 `"***"`）
- `upLinkStatus`：上联注册状态（`OFFLINE`/`ONLINE`，只读，由 `SipRegistrationServer` 维护）
- `createdAt`：创建时间（自动设置）

#### Scenario: 创建 IVS1900 互联配置
- **WHEN** 调用 `POST /api/ivs1900/interconnect` 携带有效请求体
- **THEN** 保存配置，初始 `upLinkStatus=OFFLINE`；系统等待 IVS1900 主动发起 REGISTER

#### Scenario: 查询 IVS1900 互联配置
- **WHEN** 调用 `GET /api/ivs1900/interconnect`
- **THEN** 返回当前配置列表（通常为单条），`password` 字段以 `"***"` 掩码返回，含 `upLinkStatus`

#### Scenario: 更新 IVS1900 互联配置
- **WHEN** 调用 `PUT /api/ivs1900/interconnect/{id}` 携带更新字段
- **THEN** 更新 SIP 参数；`upLinkStatus` 不受请求体影响，由系统维护

#### Scenario: 删除 IVS1900 互联配置
- **WHEN** 调用 `DELETE /api/ivs1900/interconnect/{id}`
- **THEN** 删除记录，`upLinkStatus` 清除；不存在时返回 `404 Not Found`

---

## Source: ivs1900-http-refactor

## REMOVED Requirements

### Requirement: IVS1900 HTTP 客户端封装
**Reason**: IVS1900 私有 HTTP 集成方案整体废弃，改为 GB/T 28181 标准协议。`Ivs1900HttpClient` 的统一 HTTP 请求封装不再需要。
**Migration**: 删除 `Ivs1900HttpClient` 类。

### Requirement: IVS1900 HTTP 请求 DTO
**Reason**: 私有 HTTP API 调用全部废弃，对应请求 DTO 也不再需要。
**Migration**: 删除 `ivs1900/dto/` 下的 `LoginRequest`、`LoginResponse`、`KeepAliveResponse`、`SdcCapabilityRequest`、`CameraNameRequest`、`SetDeviceConfigRequest`、`SetRsp` 等 DTO 及各响应 DTO。

### Requirement: 迁移现有调用方
**Reason**: 原迁移目标（将 HTTP 参数从 `application.yml` 迁移到 `Ivs1900InterconnectConfig` 实体）已不适用，因为私有 HTTP 调用整体废弃，无需迁移，直接删除。
**Migration**: 删除 `Ivs1900Properties`、`Ivs1900HttpConfig`、`Ivs1900SessionManager`；从 `application.yml` 删除整个 `ivs1900.*` 配置块。

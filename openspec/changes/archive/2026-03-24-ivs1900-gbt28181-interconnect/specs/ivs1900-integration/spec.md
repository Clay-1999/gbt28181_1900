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

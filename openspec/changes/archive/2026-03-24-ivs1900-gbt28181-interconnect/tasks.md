## 1. 修正 Ivs1900InterconnectConfig 实体（移除 HTTP 字段）

- [x] 1.1 新建 `domain/entity/Ivs1900InterconnectConfig.java`（已完成，但含错误的 HTTP 字段）
- [x] 1.2 新建 `domain/repository/Ivs1900InterconnectConfigRepository.java`
- [x] 1.3 从 `Ivs1900InterconnectConfig` 实体移除 `httpBaseUrl`、`httpUsername`、`httpPassword` 字段；将 `enabled` 改为 `upLinkStatus`（`LinkStatus` 枚举）

## 2. 修正 REST API（移除 HTTP 字段、移除 SipRegistrationClient 调用）

- [x] 2.1 新建 `Ivs1900InterconnectController`
- [x] 2.2 新建 `Ivs1900InterconnectRequest` / `Ivs1900InterconnectResponse` DTO
- [x] 2.3 从 `Ivs1900InterconnectRequest` / `Ivs1900InterconnectResponse` 移除 HTTP 相关字段
- [x] 2.4 修正 `Ivs1900InterconnectConfigService`：移除 `SipRegistrationClient` 调用（IVS1900 主动注册，不需要我们发起）；保存/更新时只做数据库操作
- [x] 2.5 更新前端 `Ivs1900ConfigView.vue`：移除 HTTP 配置字段（`httpBaseUrl`、`httpUsername`、`httpPassword`）

## 3. SipRegistrationServer 识别 IVS1900 REGISTER

- [x] 3.1 在 `SipRegistrationServer` 处理 REGISTER 时，检查请求方 SIP ID 是否匹配 `Ivs1900InterconnectConfigRepository.findBySipId()`
- [x] 3.2 若匹配，更新 `Ivs1900InterconnectConfig.upLinkStatus`（ONLINE/OFFLINE），并调用 `CatalogQueryService.queryCatalog()` 触发目录订阅

## 4. CatalogNotifyHandler 识别 IVS1900（已完成，保持）

- [x] 4.1 `handleNotify()` 通过 `sipId` 检查是否来自 IVS1900
- [x] 4.2 匹配则解析 XML，upsert `ivs1900_camera_mapping`，含国标 ID 生成逻辑
- [x] 4.3 不匹配则走原有 `RemoteDevice` 逻辑

## 5. 设备配置命令改走 GB/T 28181 SIP MESSAGE

- [x] 5.1 新建 `service/Ivs1900SipConfigService.java`：封装向 IVS1900 相机发 `ConfigDownload`（查询）和 `DeviceConfig`（下发）SIP MESSAGE 的逻辑，复用 `SipMessageSender`
- [x] 5.2 修改 `DeviceController` 的本端相机配置端点（`GET/PUT /api/devices/local/{gbDeviceId}/config`）：若设备属于 IVS1900（`ivs1900_camera_mapping` 有记录），改为调用 `Ivs1900SipConfigService`

## 6. 完全移除 IVS1900 私有 HTTP 组件

- [x] 6.1 删除 `Ivs1900DeviceConfigClient.java`
- [x] 6.2 删除 `Ivs1900HttpClient.java`
- [x] 6.3 删除 `Ivs1900SessionManager.java`
- [x] 6.4 删除 `Ivs1900SyncService.java`（已清空，彻底删除）
- [x] 6.5 删除 `Ivs1900HttpConfig.java`（SSL RestTemplate Bean）
- [x] 6.6 删除 `Ivs1900Properties.java` 及 `application.yml` 中的 `ivs1900.*` 配置块
- [x] 6.7 删除 `ivs1900/dto/` 目录下所有 HTTP 私有 API DTO

## 7. 验证

- [ ] 7.1 启动后端，调用 `POST /api/ivs1900/interconnect` 创建配置，确认数据库有记录
- [ ] 7.2 IVS1900 向本平台发起 REGISTER，确认日志出现 `upLinkStatus=ONLINE` 及 SUBSCRIBE Catalog 发送
- [ ] 7.3 IVS1900 推送 Catalog Notify，确认 `ivs1900_camera_mapping` 有数据写入
- [ ] 7.4 调用 `GET /api/devices`，确认 IVS1900 相机出现在本端设备列表中
- [ ] 7.5 调用 `GET /api/devices/local/{gbDeviceId}/config?type=OSDConfig`，确认向 IVS1900 发出 ConfigDownload SIP MESSAGE

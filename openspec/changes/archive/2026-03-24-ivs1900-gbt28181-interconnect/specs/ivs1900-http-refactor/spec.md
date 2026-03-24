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

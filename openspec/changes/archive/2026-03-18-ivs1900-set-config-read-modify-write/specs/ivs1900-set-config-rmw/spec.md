## ADDED Requirements

### Requirement: set 接口内置 read-modify-write
`Ivs1900DeviceConfigClient` 的 setStreamConfig / setOsdConfig / setVideoMask 接口 SHALL 在内部先调用对应 GET 接口获取设备当前完整配置，将传入的 patch 字段浅层合并后再下发，调用方无需自行执行 GET。

#### Scenario: GET 成功，patch 合并后下发
- **WHEN** 调用 setStreamConfig(cameraCode, patch)，且 getStreamConfig 返回非空结果
- **THEN** 系统将 patch 字段覆盖到 GET 结果对应字段上，以合并后的完整参数调用 doSetDeviceConfig

#### Scenario: GET 失败时返回 false
- **WHEN** 调用 setStreamConfig / setOsdConfig / setVideoMask，且对应 GET 接口返回 null 或结构不完整
- **THEN** 方法立即返回 false，不执行下发

### Requirement: DeviceConfigHandler 不再重复执行 GET
`DeviceConfigHandler` 的 applyFrameMirror / applyOsdConfig / applyPictureMask SHALL 直接构造 patch JsonNode 传给对应 set 接口，不再自行调用 GET 接口或手动 merge。

#### Scenario: applyOsdConfig 简化调用
- **WHEN** 收到 DeviceConfig OSDConfig 命令
- **THEN** handler 从 XML 中提取 OSDEnabled / OSDTime / OSDFontSize 构造 patch，直接调用 setOsdConfig(cameraCode, patch)

#### Scenario: applyPictureMask 简化调用
- **WHEN** 收到 DeviceConfig PictureMask 命令
- **THEN** handler 从 XML 中提取 MaskEnabled 等字段构造 patch，直接调用 setVideoMask(cameraCode, patch)

#### Scenario: applyFrameMirror 简化调用
- **WHEN** 收到 DeviceConfig FrameMirror 命令
- **THEN** handler 构造含 frameMirrorMode 字段的 patch，直接调用 setStreamConfig(cameraCode, patch)

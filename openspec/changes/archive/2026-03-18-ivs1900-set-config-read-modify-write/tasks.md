## 1. Ivs1900DeviceConfigClient

- [x] 1.1 新增私有方法 `mergeInto(ObjectNode base, JsonNode patch)`，用 `base.setAll((ObjectNode) patch)` 实现浅层字段覆盖
- [x] 1.2 修改 `setStreamConfig`：先调用 `getStreamConfig` 获取完整参数，提取 `cameraStreamConfig` 节点，merge patch 后传给 `doSetDeviceConfig`；GET 失败返回 false
- [x] 1.3 修改 `setOsdConfig`：先调用 `getOsdConfig` 获取完整参数，提取 `cameraOSDConfig` 节点，merge patch 后传给 `doSetDeviceConfig`；GET 失败返回 false
- [x] 1.4 修改 `setVideoMask`：先调用 `getVideoMask` 获取完整参数，提取 `videoMask` 节点，merge patch 后传给 `doSetDeviceConfig`；GET 失败返回 false

## 2. DeviceConfigHandler 简化

- [x] 2.1 简化 `applyFrameMirror`：删除 GET + forEach 逻辑，直接构造含 `frameMirrorMode` 字段的 patch ObjectNode，调用 `configClient.setStreamConfig(cameraCode, patch)`
- [x] 2.2 简化 `applyOsdConfig`：删除 GET + 手动字段赋值逻辑，从 XML 提取 OSDEnabled / OSDTime / OSDFontSize 构造 patch ObjectNode，调用 `configClient.setOsdConfig(cameraCode, patch)`
- [x] 2.3 简化 `applyPictureMask`：删除 GET + valueToTree 逻辑，从 XML 提取 enableVideoMask 等字段构造 patch ObjectNode，调用 `configClient.setVideoMask(cameraCode, patch)`
- [x] 2.4 `applyVideoParamAttribute` 保持现有逻辑不变（StreamID 匹配逻辑仍在 handler 层），确认无需调整

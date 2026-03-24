## Why

`Ivs1900DeviceConfigClient` 的 set 接口（setStreamConfig / setOsdConfig / setVideoMask）直接将调用方传入的 JsonNode 下发给 IVS1900，要求调用方自行先 GET 完整参数再 patch，导致 read-modify-write 逻辑散落在 `DeviceConfigHandler` 各方法中，重复且难以维护。应将 read-modify-write 收归 client 层，调用方只需传入要修改的字段。

## What Changes

- `setStreamConfig(cameraCode, patch)`：内部先调用 `getStreamConfig` 获取完整参数，merge patch 后下发
- `setOsdConfig(cameraCode, patch)`：内部先调用 `getOsdConfig` 获取完整参数，merge patch 后下发
- `setVideoMask(cameraCode, patch)`：内部先调用 `getVideoMask` 获取完整参数，merge patch 后下发
- 新增私有方法 `mergeInto(base, patch)` 做浅层字段覆盖
- `DeviceConfigHandler.applyVideoParamAttribute` / `applyFrameMirror` / `applyOsdConfig` / `applyPictureMask` 中的 GET 调用和手动 merge 逻辑全部删除，改为直接构造 patch 传给 set 接口
- `setCameraName` 和 `doSetDeviceConfig` 签名不变

## Capabilities

### New Capabilities

- `ivs1900-set-config-rmw`: IVS1900 配置下发接口内置 read-modify-write，调用方只传 patch 字段

### Modified Capabilities

（无 spec 级别的行为变更）

## Impact

- `Ivs1900DeviceConfigClient`：set 方法内部增加 GET 调用
- `DeviceConfigHandler`：applyVideoParamAttribute / applyFrameMirror / applyOsdConfig / applyPictureMask 简化，删除重复的 GET + merge 代码
- 对外接口签名不变，无 API 破坏性变更

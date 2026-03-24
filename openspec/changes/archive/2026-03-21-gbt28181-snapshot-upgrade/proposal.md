## Why

GB/T 28181-2022 §9.14 图像抓拍和 §9.13 软件升级是两项低复杂度的纯信令功能，目前尚未实现。两者均通过 SIP MESSAGE（MANSCDP）触发，无需媒体流，实现门槛低，可快速补全标准信令覆盖。

## What Changes

- 新增 REST 接口 `POST /api/devices/{type}/{deviceId}/snapshot`：北向触发图像抓拍，通过 SIP MESSAGE 发送 `SnapShotConfig` 控制命令到设备
- 新增 REST 接口 `POST /api/devices/{type}/{deviceId}/upgrade`：北向触发 OTA 固件升级，通过 SIP MESSAGE 发送 `DeviceUpgrade` 控制命令到设备
- 新增南向路由：`DeviceCommandRouter` 处理入站 `SnapShotConfig` 和 `DeviceUpgrade` 控制命令（从上游平台转发过来时路由到本端或下游）
- 新增 DTO：`SnapshotRequest`、`UpgradeRequest`

## Capabilities

### New Capabilities

- `snapshot-command`: 图像抓拍命令——北向 REST 触发、SIP MESSAGE SnapShotConfig 发送、南向入站路由
- `device-upgrade-command`: 固件升级命令——北向 REST 触发、SIP MESSAGE DeviceUpgrade 发送、南向入站路由

### Modified Capabilities

- `device-command-router`: 新增 SnapShotConfig 和 DeviceUpgrade 入站路由分支

## Impact

- 新增文件：`SnapshotRequest.java`、`UpgradeRequest.java`（DTO）
- 修改文件：`DeviceController.java`（+2 端点）、`DeviceCommandRouter.java`（+2 路由分支）、`DeviceControlService.java`（+2 发送方法）
- 无数据库变更，无媒体流，不影响现有功能

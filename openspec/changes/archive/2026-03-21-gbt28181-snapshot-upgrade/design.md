## Context

项目已实现 GuardCmd、RecordCmd、TeleBoot 等设备控制命令（§9.3），结构统一：北向 REST 端点 → `DeviceControlService.send*()` → `SipMessageSender.sendMessage()` → SIP MESSAGE；南向入站命令经 `DeviceCommandRouter` 识别 CmdType 后路由处理。

本次新增 **§9.14 图像抓拍**（`SnapShotConfig`）和 **§9.13 软件升级**（`DeviceUpgrade`）两个控制命令，复用完全相同的架构模式。

## Goals / Non-Goals

**Goals:**
- 北向：`POST /api/devices/{type}/{deviceId}/snapshot` 触发抓拍，`POST /api/devices/{type}/{deviceId}/upgrade` 触发 OTA
- 南向：`DeviceCommandRouter` 识别 `SnapShotConfig` 和 `DeviceUpgrade` CmdType 并路由
- 设备不存在时返回 404（复用 `ResourceNotFoundException` 机制）

**Non-Goals:**
- 不实现图像上传接收服务（设备直接 HTTP POST 到 `UploadAddr`，平台不参与）
- 不实现升级状态轮询（设备升级结果通过重新注册体现）
- 不实现前端 UI

## Decisions

**复用 `DeviceControlService`**：新增 `sendSnapshot()` 和 `sendUpgrade()` 方法，与 `sendGuardCmd()`/`sendReboot()` 模式完全一致——构造 XML，调 `sendMessage()`。无需新建 Service 类。

**新增两个 DTO**：`SnapshotRequest`（含 `snapNum`、`interval`、`uploadAddr`、`resolution`）和 `UpgradeRequest`（含 `firmwareId`、`firmwareAddr`）。保持与现有 `DeviceControlRequest` 风格一致（Lombok `@Data`）。

**南向路由**：在 `DeviceCommandRouter.route()` 的 CmdType 判断链中新增两个 `if` 分支，先回 200 OK，再路由到本端或外域，与 `handleGuardCmd()` / `handleDeviceControl()` 逻辑完全对称。

## Risks / Trade-offs

- [抓拍 UploadAddr 由调用方传入] → 平台不验证 URL 合法性，不安全输入原样透传给设备。可接受——调用方为内部系统。
- [升级命令无确认机制] → 命令发出后无法确认设备是否开始升级。符合 GB/T 28181 标准行为（设备升级后通过重新注册来体现）。

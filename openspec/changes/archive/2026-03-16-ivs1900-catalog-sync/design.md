## Context

Phase 4 完成了 SIP 注册与心跳保活。`Ivs1900SessionManager` 已实现登录与保活，`Ivs1900CameraMapping` 实体和 Repository 已存在但尚未填充数据。本 change 实现 IVS1900 相机同步和设备目录发布。

## Goals / Non-Goals

**Goals:**
- 定时从 IVS1900 同步相机列表，维护 `ivs1900_camera_mapping` 表
- 响应对端 `SUBSCRIBE Catalog`，发送包含本端相机的 `NOTIFY`
- 提供 REST API 供前端查询本端设备列表
- 前端设备树页展示本端相机

**Non-Goals:**
- 不实现对端设备目录同步（REMOTE 设备）
- 不实现 PTZ 控制、设备参数配置
- 不实现媒体流代理

## Decisions

**决策 1：国标 ID 生成使用 `ivs1900_camera_mapping.id` 作为序号**

`gb_device_id = domainCode前10位 + "132" + String.format("%07d", id)`

`id` 是自增主键，保证唯一且不变。`domainCode` 取 IVS1900 相机的 `domainCode` 字段前 10 位（纯数字部分）。若 `domainCode` 非纯数字，取本端 SIP 设备 ID 前 10 位作为兜底。

**决策 2：SUBSCRIBE 处理采用同步 NOTIFY 模式**

收到 `SUBSCRIBE` 后：
1. 立即回复 `200 OK`
2. 从 `ivs1900_camera_mapping` 读取全量数据
3. 组装 XML，通过 `SipProvider.sendRequest()` 发送 `NOTIFY`

不维护订阅状态，每次 SUBSCRIBE 触发一次全量 NOTIFY，简化实现。

**决策 3：NOTIFY XML 使用 GB/T 28181-2022 标准格式**

`Status` 字段：`ONLINE` → `"ON"`，`OFFLINE` → `"OFF"`

**决策 4：设备列表 API 直接查 DB，不缓存**

数据量小（通常 < 1000 台），直接查 `ivs1900_camera_mapping` 表即可，无需额外缓存层。

**决策 5：前端设备树页轮询刷新（30 秒）**

与互联管理页保持一致的轮询策略。

## Risks / Trade-offs

- [风险] IVS1900 `domainCode` 为 32 位十六进制字符串，前 8 位非纯数字 → 兜底使用本端 SIP 设备 ID 前 8 位
- [风险] 相机数量超过 9999999（7位序号上限）→ MVP 阶段不处理，记录警告日志
- [风险] SUBSCRIBE 无状态设计导致对端重复订阅时重复发送 NOTIFY → 可接受，GB/T 28181 允许重复 NOTIFY

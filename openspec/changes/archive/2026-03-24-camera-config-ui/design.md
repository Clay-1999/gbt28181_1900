## Context

设备管理平台已有本端 IVS1900 相机和外域 GBT28181 设备的列表展示。本端配置通过 `Ivs1900DeviceConfigClient` 的 get/set 方法访问；外域配置通过 GBT28181 SIP MESSAGE（ConfigDownload/DeviceConfig）交互。`RemoteDeviceMessageForwarder` 已有透传 SIP 请求的 pending future 机制，可复用于主动发起配置查询。

## Goals / Non-Goals

**Goals:**
- REST API 支持本端和外域设备的配置查询（GET）和下发（PUT）
- 支持 4 种配置类型：VideoParamAttribute、OSDConfig、PictureMask、FrameMirror
- 前端对话框展示当前配置并支持修改保存
- 外域配置通过 SIP 异步交互，REST 接口同步等待结果（超时 10s）

**Non-Goals:**
- BasicParam 配置（不在本次范围）
- 批量配置多台设备
- 配置历史记录或审计日志

## Decisions

**D1：每种配置类型使用独立 URL，不用 query param 区分**
16 个 endpoint（本端 8 + 外域 8），路径形如 `/api/devices/local/{id}/config/osd`。每个 endpoint 对应明确的业务语义，避免 query param 导致的路由歧义，也便于前端按配置类型直接调用。

**D2：外域配置 REST 接口同步等待 SIP 响应（10s 超时）**
REST 调用方期望同步结果。`RemoteDeviceMessageForwarder` 已有 `CompletableFuture` pending 机制，`get(10, SECONDS)` 即可实现同步等待。超时返回 504。

**D3：外域配置响应 MESSAGE 的识别**
外域平台回复的 MESSAGE 中 CmdType 为 ConfigDownload/DeviceConfig，SN 与请求一致。在 `GbtSipListener.processRequest` 中，MESSAGE 先经过 `DeviceCommandRouter.route()`，若 route 返回 false 再走 `sipRegistrationServer.handleMessage()`。需要在 route 之前或 route 内部识别"这是对我们主动发出请求的响应"并 complete future。

实现方式：在 `RemoteDeviceMessageForwarder` 新增 `onIncomingResponse(sn, xmlBody)` 方法，在 `GbtSipListener` 的 MESSAGE 处理中，解析 CmdType 和 SN，若 pending 中有对应 key 则调用该方法，不再走 route。

**D4：本端配置逻辑封装在 `DeviceConfigService`**
`DeviceController` 保持薄，业务逻辑（查找 camera mapping、调用 client、转换 Map）放在 service 层，与现有 `LocalSipConfigService` 模式一致。

**D5：前端配置对话框内嵌在 DevicesView.vue**
与 `InterconnectsView.vue` 的编辑对话框模式一致，不新建独立页面，减少路由复杂度。

## Risks / Trade-offs

- [外域 SIP 响应超时] 对端平台不响应时 REST 接口等待 10s 后返回 504 → 前端显示超时提示
- [外域 SN 冲突] 同时发起多个配置请求时 SN 可能重复 → 用 UUID 生成唯一 SN（数字截断为 5 位改为用 callId 作 key）
- [Map 类型安全] PUT 时前端传入非法字段会被忽略或导致 IVS1900 报错 → 依赖 IVS1900 的错误响应，返回 false 时前端提示失败

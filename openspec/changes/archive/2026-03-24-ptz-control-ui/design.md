## Context

当前 `DevicesView.vue` 中的实况播放对话框（`el-dialog` width=720px）仅包含一个 FLV 视频播放区域。`ptzType` 字段已通过 Catalog Notify 同步并存储，前端 `formatPtzType()` 已可识别球机（1）、遥控枪机（4）、遥控半球（5）等可控类型。后端 `SipMessageSender` 已具备向任意设备发送 SIP MESSAGE 的能力，`DeviceControl` / `PresetQuery` / `CruiseTrackListQuery` / `CruiseTrackQuery` 报文只需参照标准构建 XML body 发送。

## Goals / Non-Goals

**Goals:**
- 实况播放对话框改为左右分栏：左侧视频（约 60%），右侧控制面板（约 40%），仅球机/遥控类显示控制面板
- 云台控制：8 方向 + 停止，变倍/变焦/光圈，`mousedown` 开始发命令 `mouseup` 发停止
- 预置位：查询列表、调用、设置（以当前镜头位置命名保存）、删除
- 巡航轨迹：查询轨迹列表、查询单条轨迹详情、启动/停止巡航
- 后端新增统一 REST 入口：`POST /api/devices/{type}/{id}/ptz/control`、`GET/POST /api/devices/{type}/{id}/ptz/preset`、`GET /api/devices/{type}/{id}/ptz/cruise`

**Non-Goals:**
- PTZ 精准位置查询/订阅（`PTZPosition`）
- 拉框放大/缩小控制
- 目标跟踪控制
- 看守位配置（已在 DeviceControl 配置 tab 中）

## Decisions

### 1. PTZCmd 编码方式

GB/T 28181 A.3 规定 PTZCmd 为 8 字节十六进制编码。本次前端只传语义动作（如 `{ action: "left", speed: 5 }`），后端统一编码为 PTZCmd 字节串，避免前端耦合协议细节。

### 2. 持续控制（mousedown/mouseup）

PTZ 方向控制需要持续发命令并在松开时发停止。前端用 `@mousedown` 触发开始（发方向命令）、`@mouseup` / `@mouseleave` 触发停止（发停止命令），无需轮询。

### 3. 查询接口的同步 vs 异步

预置位查询（`PresetQuery`）和巡航查询需要等待设备响应 MESSAGE。与 ConfigDownload 相同，采用 `CompletableFuture` + 10s 超时的 pending map 机制，统一通过 SN 匹配回调，超时返回 HTTP 504。

### 4. 控制面板仅对可控类型显示

通过 `ptzType` 判断：1（球机）/ 4（遥控枪机）/ 5（遥控半球）显示控制面板，其余类型只显示视频。对话框宽度：有控制面板时 1100px，无时保持 720px。

### 5. 后端路由

复用 `DeviceController` 扩展，分本端（IVS1900）和外域（RemoteDevice）两条路径。本端走 `Ivs1900SipConfigService`（复用 SIP MESSAGE 发送逻辑），外域走 `RemoteDeviceMessageForwarder`。

## Risks / Trade-offs

- **设备不响应 PresetQuery**：部分球机可能不实现此查询；前端收到 504 时显示提示"设备不支持预置位查询"，不阻塞云台控制功能。
- **PTZCmd 字节编码复杂**：A.3 规定的编码需要仔细实现，速度值、命令码各位有明确定义；后端统一封装可减少出错面。
- **布局宽度变化**：对话框从 720px 扩展到 1100px，需确认小屏幕下可滚动，不强制满屏。

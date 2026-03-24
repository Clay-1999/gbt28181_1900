## Context

当前平台通过 SIP INVITE + RTP 实现实时视频播放，但没有历史录像查询能力。GB/T 28181 A.2.4.5 定义了文件目录检索（RecordInfo）协议，通过 SIP MESSAGE 携带 XML 查询请求，设备返回包含录像时间段列表的响应。

现有代码中 `PtzService` 已建立了"发送 SIP MESSAGE + CompletableFuture 等待响应"的模式，可以直接复用。`DeviceCommandRouter` 已处理多种 CmdType 路由，可扩展。

## Goals / Non-Goals

**Goals:**
- 通过 SIP RecordInfo MESSAGE 向本端（IVS1900）和外域设备查询历史录像列表
- REST 接口接收时间范围参数，返回录像条目列表（JSON）
- 前端新增"录像"按钮和录像查询对话框，支持日期时间选择与列表展示

**Non-Goals:**
- 录像回放（SIP INVITE with history range）——留待后续 change
- 分页处理多批次 RecordInfo 响应（设备可能分多次返回，当前只取第一批）
- 录像下载

## Decisions

### 1. 复用 PtzService 的 pending future 机制

与 PTZ 预置位查询相同：发送 MESSAGE 时生成 SN，注册 `CompletableFuture<String>` 到 map，收到响应 MESSAGE 时通过 SN 匹配完成 future。10s 超时后返回 null（前端显示超时提示）。

替代方案：新建 callback 机制。不采用——代码量更大，行为与现有模式不一致。

### 2. RecordInfo 响应可能分多批次

GB/T 28181 允许设备分多条 MESSAGE 返回录像列表（每条 `<RecordList Num="N">`）。当前设计：收到第一条响应即 complete future，后续批次丢弃。若 SumNum > 已收录数量，前端提示"结果可能不完整"。

替代方案：等待所有批次。不采用——超时逻辑复杂，多数场景录像条目不多。

### 3. 查询参数

必选：`startTime`、`endTime`（ISO-8601 格式）。可选：`type`（time/alarm/manual/all，缺省 all）。其余 RecordInfo 可选字段（FilePath、Address、RecorderID 等）暂不暴露。

### 4. 本端 vs 外域统一端点

沿用现有 `{type}/{deviceId}` 路由风格：
- `POST /api/devices/local/{gbDeviceId}/records/query` → 通过 IVS1900 互联配置发送
- `POST /api/devices/remote/{deviceId}/records/query` → 通过 RemoteDevice 所属互联配置发送

## Risks / Trade-offs

- [多批次响应丢失] → 前端展示 SumNum 与实际返回数量，用户可感知是否缺失
- [SN 碰撞] → 与 PTZ 使用相同 `System.currentTimeMillis() % 100000` 生成，极低概率碰撞，可接受
- [设备不支持 RecordInfo] → 超时后返回 504，前端提示"设备不支持录像查询"

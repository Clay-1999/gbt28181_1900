## Context

实时流（§9.2）已由 `SipInviteService` + `StreamSession` + `StreamSessionStore` 实现，SDP `s=Play`，会话由 callId 跟踪。回放（§9.8）流程几乎相同，主要差异：
- SDP `s=Playback`，`t=<开始时间戳> <结束时间戳>` 字段必填，SSRC 首位为 `1`（区分实时与回放）
- 会话建立后，通过 SIP INFO 发送 MANSRTSP 命令（PLAY/PAUSE/SCALE/TEARDOWN）实现回放控制
- 回放会话独立于实时流会话，用单独的 `PlaybackSession` 管理

## Goals / Non-Goals

**Goals:**
- `POST /api/devices/{type}/{deviceId}/playback/start`：发起回放 INVITE，返回流 URL
- `POST /api/devices/{type}/{deviceId}/playback/stop`：终止回放（BYE）
- `POST /api/devices/{type}/{deviceId}/playback/control`：发送 INFO MANSRTSP（PLAY/PAUSE/SCALE）
- 回放 SDP 包含正确的 `s=Playback`、`t=` 时间范围、`y=1xxxxxxxxx` SSRC

**Non-Goals:**
- 文件下载（§9.9）——同结构，后续再做
- 回放录像列表 UI（仅后端信令）
- 多路并发回放管理（当前 per-device 单路）

## Decisions

**复用 `SipInviteService` 的 INVITE 发送基础设施**：`SipPlaybackService` 注入 `SipMessageSender`，调用已有的 `sendInvite()` 方法，但传入 `s=Playback` 的 SDP。不修改 `SipInviteService`，避免影响实时流功能。

**独立的 `PlaybackSessionStore`**：key = `deviceId`，value 含 `callId`、`startTime`、`endTime`、起始 ZLM 端口。与 `StreamSessionStore` 完全隔离，避免互相干扰。

**INFO 发送**：在 `SipMessageSender` 新增 `sendInfo(callId, body)` 方法，复用现有 SIP 栈（`sipProvider`、`clientTransactions`）。INFO 体格式为 MANSRTSP/1.0 文本（非 MANSCDP XML）。

**`GbtSipListener.processResponse`** 扩展：INFO 200 OK 和 BYE 200 OK 均通过 CSeq method 分发——INFO 响应由 `SipPlaybackService.onInfoOk()` 处理（实际为 fire-and-forget，可忽略）。

**SSRC 规则**：实时流 `0xxxxxxxxx`（首位 0），回放 `1xxxxxxxxx`（首位 1），符合 GB/T 28181 规范。

## Risks / Trade-offs

- [ZLM 端口复用] → 回放会话与实时流共用 ZLM RTP 端口分配，若同时回放+实时同一设备可能端口冲突 → 当前允许 per-device 单路，start 前先 stop 旧会话。
- [INFO MANSRTSP 设备兼容性] → 部分设备可能不支持 PAUSE/SCALE，平台发送后不等待效果确认，属标准行为。

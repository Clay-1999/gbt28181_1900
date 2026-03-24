## Context

GB/T 28181-2022 §9.11 语音广播和 §9.12 语音对讲均通过 SIP INVITE 信令建立音频媒体会话，两者信令流程高度相同，区别仅在于：
- 语音广播：SDP `s=Broadcast`，`a=sendonly`，单向 RTP（平台 → 设备）
- 语音对讲：SDP `s=Talk`，`a=sendrecv`，双向 RTP（平台 ↔ 设备）

现有 `SipInviteService`（视频流）和 `SipPlaybackService`（录像回放）均采用"pendingInvites ConcurrentHashMap + CompletableFuture + 10s 等待"模式，`SipAudioService` 直接复用该模式。ZLMediaKit 通过 `openRtpServer` 分配接收端口；对讲场景需同时记录设备返回的 RTP 端口，供 ZLM 向设备推流使用。

## Goals / Non-Goals

**Goals:**
- 通过 SIP INVITE 向本端/外域设备发起语音广播（sendonly）和语音对讲（sendrecv）
- 经 ZLMediaKit RTP 端口中继音频媒体
- REST 接口控制启停，支持 local/remote 两类设备
- 幂等停止：会话不存在时 stopAudio 正常返回，不报错

**Non-Goals:**
- 前端音频页面（本 change 只做后端信令层）
- 音频转码（ZLM 直接中继 G.711/G.726 RTP 流）
- 多路并发音频会话（每个 deviceId 只维护一个活跃音频会话）

## Decisions

### 1. AudioSession 独立于 StreamSession

音频会话与视频流会话生命周期完全独立，且字段有差异（多了 `deviceRtpIp`、`deviceRtpPort`、`mode`）。共用 StreamSessionStore 会引入不必要的字段污染。使用独立 `AudioSession` record + `AudioSessionStore`。

替代方案：在 StreamSession 加 nullable 字段区分音视频。不采用——混合职责，判断逻辑复杂。

### 2. GbtSipListener 200 OK 路由顺序

收到 INVITE 200 OK 时，按以下顺序检查（先精准后通用）：
1. `audioSessionService.hasCallId(callId)` → `audioSessionService.onInviteOk(event)`
2. `playbackService.hasCallId(callId)` → `playbackService.onInviteOk(event)`
3. `sipInviteService.onInviteOk(event)`（视频流，兜底）

替代方案：统一 registry 注册。不采用——现有代码未建立此机制，引入复杂度不划算。

### 3. SDP 音频格式

使用 G.711 μ-law（PCMU，payload 0）和 G.711 A-law（PCMA，payload 8），这是 GB/T 28181 要求的基本格式，ZLM 支持。不包含视频媒体行。

```
m=audio <zlmPort> RTP/AVP 0 8
a=rtpmap:0 PCMU/8000
a=rtpmap:8 PCMA/8000
a=sendonly   （广播）或   a=sendrecv   （对讲）
y=<ssrc>
```

### 4. SSRC 生成

与 SipInviteService 保持一致：`String.format("0%09d", (long)(Math.random() * 1_000_000_000L))`。

### 5. 对讲场景 ZLM 双向推流

设备 200 OK SDP 中含设备 RTP 端口（`m=audio <devicePort> RTP/AVP ...`），`SipAudioService.onInviteOk` 解析该端口和 IP，存入 `AudioSession.deviceRtpPort` / `deviceRtpIp`。ZLM 推流到设备的操作由调用方（上层应用层）通过单独 ZLM API 发起；本 change 只保证信令层正确建立并记录设备 RTP 信息。

替代方案：在 onInviteOk 内直接调用 ZLM 推流 API。不采用——ZLM 推流 API 细节尚未确定，且关注点分离更清晰。

## Data Structures

### AudioSession (record)

```java
public record AudioSession(
    String deviceId,     // 目标设备 SIP ID（会话 key）
    String callId,       // SIP Call-ID
    String fromTag,      // INVITE 发出时的 From-tag
    String toTag,        // 200 OK 中的 To-tag（ACK 后填入）
    long   cseq,         // CSeq 序列号
    String mode,         // "broadcast" 或 "talk"
    String streamId,     // ZLM stream ID（格式：audio_broadcast_{deviceId} 或 audio_talk_{deviceId}）
    int    zlmPort,      // ZLM 分配的 RTP 接收端口
    String deviceRtpIp,  // 设备 RTP IP（从 200 OK SDP 解析，对讲场景使用）
    int    deviceRtpPort // 设备 RTP 端口（从 200 OK SDP 解析，对讲场景使用；广播填 0）
) {}
```

### AudioSessionStore (Spring @Component)

- 内部存储：`ConcurrentHashMap<String, AudioSession>`，key = deviceId
- 方法：`put(AudioSession)`，`get(deviceId)`，`has(deviceId)`，`remove(deviceId)`，`findByCallId(callId)`

## SipAudioService

```
startAudio(type: String, deviceId: String, mode: String) → void
  1. 若已有活跃音频会话，先 stopAudio
  2. ZLM openRtpServer(streamId)，获取 zlmPort
  3. 构造音频 SDP（按 mode 选择 s= 和 a=方向属性）
  4. 生成 callId，注册 pendingInvites future
  5. sipMessageSender.sendInvite(config, deviceId, callId, sdp, ssrc)
  6. future.get(10s)
  7. 解析 200 OK SDP，提取 deviceRtpIp/deviceRtpPort
  8. sendAck
  9. 构建 AudioSession，存入 AudioSessionStore
  10. 返回（调用方可再发起 ZLM 推流）
  异常：TimeoutException → closeRtpServer，抛 IllegalStateException("INVITE timeout")

stopAudio(type: String, deviceId: String) → void
  1. audioSessionStore.get(deviceId)，若不存在直接 return（幂等）
  2. sipMessageSender.sendBye(session, config)
  3. zlmClient.closeRtpServer(session.streamId())
  4. audioSessionStore.remove(deviceId)

onInviteOk(ResponseEvent event) → void
  提取 callId，complete 对应 pendingInvites future

hasCallId(String callId) → boolean
  检查 pendingInvites.containsKey(callId)

onRemoteBye(String callId) → void
  audioSessionStore.findByCallId(callId) → closeRtpServer，remove
```

## GbtSipListener 修改点

**processResponse（INVITE 200 OK）**：
```
if (audioService.hasCallId(callId)) {
    audioService.onInviteOk(event);
} else if (playbackService.hasCallId(callId)) {
    playbackService.onInviteOk(event);
} else {
    sipInviteService.onInviteOk(event);
}
```

**processRequest（BYE）**：
```
// 在现有 sipInviteService.onRemoteBye(callId) 之前，先检查音频会话
if (audioSessionStore.has(callId via findByCallId)) {
    audioService.onRemoteBye(callId);
} else {
    sipInviteService.onRemoteBye(callId);
}
```

## REST API

AudioController 路径前缀：`/api/devices/{type}/{deviceId}/audio`
- `POST .../broadcast/start` — 返回 200 OK（body 空）；设备不存在 404；INVITE 超时 504
- `POST .../broadcast/stop`  — 返回 200 OK；幂等（无会话时也返回 200）
- `POST .../talk/start`      — 同 broadcast/start
- `POST .../talk/stop`       — 同 broadcast/stop

`{type}` 取值：`local`（IVS1900 本端相机）或 `remote`（外域设备）

## Risks / Trade-offs

- [ZLM 双向推流未实现] 对讲场景 ZLM 推流部分留给后续 change；当前只完成信令层和 RTP 接收端口分配
- [SDP 解析简化] 从 200 OK SDP 中提取 deviceRtpPort 使用简单字符串解析（取 `m=audio` 行第二字段），不引入完整 SDP 解析库
- [无心跳保活] GB/T 28181 音频会话无专用心跳，会话生命周期由 BYE 控制；设备掉线时会话不会自动清理（可接受，与视频流行为一致）

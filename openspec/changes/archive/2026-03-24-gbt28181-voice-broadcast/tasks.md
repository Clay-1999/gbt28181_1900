## 1. 数据结构

- [x] 1.1 新建 `sip/AudioSession.java`（Java record），字段：`deviceId`（String）、`callId`（String）、`fromTag`（String）、`toTag`（String）、`cseq`（long）、`mode`（String，"broadcast" 或 "talk"）、`streamId`（String）、`zlmPort`（int）、`deviceRtpIp`（String）、`deviceRtpPort`（int）
- [x] 1.2 新建 `sip/AudioSessionStore.java`（@Component），内部 `ConcurrentHashMap<String, AudioSession>`，实现方法：`put(AudioSession)`、`get(String deviceId) → Optional<AudioSession>`、`has(String deviceId) → boolean`、`remove(String deviceId)`、`findByCallId(String callId) → Optional<AudioSession>`

## 2. SipAudioService

- [x] 2.1 新建 `sip/SipAudioService.java`（@Component），注入 `SipMessageSender`、`AudioSessionStore`、`ZLMediaKitClient`、`ZlmConfig`、`InterconnectConfigRepository`、`RemoteDeviceRepository`、`Ivs1900CameraMappingRepository`；维护 `ConcurrentHashMap<String, CompletableFuture<ResponseEvent>> pendingInvites`（key = callId）；实现 `setSipProvider(SipProvider)` 方法
- [x] 2.2 实现 `startAudio(String type, String deviceId, String mode)`：
  - 查找设备和互联配置（type 为 "local" 时查 `cameraMappingRepo`，"remote" 时查 `remoteDeviceRepo` + `interconnectConfigRepo`）；不存在抛 `IllegalArgumentException`
  - 若已有活跃音频会话，先 `stopAudio(type, deviceId)`
  - `zlmClient.openRtpServer(streamId)`（streamId = `audio_broadcast_` 或 `audio_talk_` + deviceId 去特殊字符）；失败抛 `IllegalStateException`
  - 构造音频 SDP（v=0、o=、s=Broadcast 或 s=Talk、c=、t=0 0、m=audio、a=rtpmap:0 PCMU/8000、a=rtpmap:8 PCMA/8000、a=sendonly 或 a=sendrecv、y=ssrc）
  - 生成 callId（UUID），注册 `CompletableFuture<ResponseEvent>` 到 pendingInvites
  - `sipMessageSender.sendInvite(config, deviceId, callId, sdp, ssrc)`，提取 fromTag
  - `future.get(10, SECONDS)`；超时 catch TimeoutException → `closeRtpServer`，抛 `IllegalStateException("INVITE timeout")`
  - 解析 200 OK SDP：提取 `m=audio` 行端口（deviceRtpPort）和 `c=IN IP4` 行 IP（deviceRtpIp）
  - `sipMessageSender.sendAck(resp200, fromTag)`，提取 toTag
  - 构建 `AudioSession`，`audioSessionStore.put(session)`
  - finally: `pendingInvites.remove(callId)`
- [x] 2.3 实现 `stopAudio(String type, String deviceId)`：
  - `audioSessionStore.get(deviceId)` 不存在则直接 return（幂等）
  - 查找互联配置；若 config 不为 null，`sipMessageSender.sendBye(session, config)`（将 AudioSession 适配为 sendBye 所需参数，或新增 `sendBye(AudioSession, InterconnectConfig)` 重载）
  - `zlmClient.closeRtpServer(session.streamId())`
  - `audioSessionStore.remove(deviceId)`
- [x] 2.4 实现 `onInviteOk(ResponseEvent event)`：提取 callId，`pendingInvites.get(callId)` 不为 null 则 `future.complete(event)`
- [x] 2.5 实现 `hasCallId(String callId) → boolean`：`return pendingInvites.containsKey(callId)`
- [x] 2.6 实现 `onRemoteBye(String callId)`：`audioSessionStore.findByCallId(callId).ifPresent(session → closeRtpServer + remove)`

## 3. GbtSipListener 扩展

- [x] 3.1 在 `GbtSipListener` 中注入 `SipAudioService`（与 SipInviteService 同模式）；在 `SipStackManager` 的 `setSipProvider` 中调用 `audioService.setSipProvider(sipProvider)`
- [x] 3.2 修改 `processResponse` 中 INVITE 200 OK 分发逻辑：在调用 `sipInviteService.onInviteOk(event)` 之前，先检查 `audioService.hasCallId(callId)` → 若 true 则调 `audioService.onInviteOk(event)`；否则继续原有检查链（回放 → 视频流）
- [x] 3.3 修改 `processRequest` 中 BYE 处理逻辑：提取 callId 后，先检查 `audioSessionStore.findByCallId(callId).isPresent()` → 若 true 则调 `audioService.onRemoteBye(callId)`；否则交给 `sipInviteService.onRemoteBye(callId)`

## 4. REST 端点（AudioController）

- [x] 4.1 新建 `api/controller/AudioController.java`（@RestController，@RequestMapping("/api/devices")），注入 `SipAudioService`
- [x] 4.2 实现 `POST /api/devices/{type}/{deviceId}/audio/broadcast/start`：调用 `audioService.startAudio(type, deviceId, "broadcast")`；catch `IllegalArgumentException` → 404；catch `IllegalStateException(含 "timeout")` → 504；其余异常 → 500；成功 → 200
- [x] 4.3 实现 `POST /api/devices/{type}/{deviceId}/audio/broadcast/stop`：调用 `audioService.stopAudio(type, deviceId)`；成功 → 200（幂等，不抛异常）
- [x] 4.4 实现 `POST /api/devices/{type}/{deviceId}/audio/talk/start`：同 4.2，mode 为 "talk"
- [x] 4.5 实现 `POST /api/devices/{type}/{deviceId}/audio/talk/stop`：同 4.3


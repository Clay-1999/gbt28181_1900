## Why

当前平台只支持视频流（实时播放、录像回放），没有音频通信能力。在安防场景中，远程喊话（语音广播）和双向对讲是核心运营需求——例如：发现异常时对监控区域广播警告、值班人员与现场工作人员实时通话。GB/T 28181-2022 §9.11 和 §9.12 分别定义了语音广播（单向，平台 → 设备）和语音对讲（双向，平台 ↔ 设备）的 SIP 信令流程，当前平台未实现这两个能力。

## What Changes

- 新建 `AudioSession` 数据结构（record），存储音频会话状态（deviceId、callId、fromTag、toTag、cseq、mode、streamId、zlmPort、deviceRtpIp、deviceRtpPort）
- 新建 `AudioSessionStore`，key = deviceId，支持 put/get/remove/findByCallId，与 StreamSessionStore 同模式
- 新建 `SipAudioService`，实现：
  - `startAudio(type, deviceId, mode)` — 向 ZLM 申请 RTP 端口，构造音频 SDP（s=Broadcast/s=Talk），发送 SIP INVITE，等待 200 OK，发 ACK，存储 AudioSession
  - `stopAudio(type, deviceId)` — 发送 BYE，释放 ZLM RTP 端口，清理会话（幂等）
  - `onInviteOk(event)` / `onRemoteBye(callId)` — 由 GbtSipListener 回调
- 修改 `GbtSipListener`：INVITE 200 OK 路由优先检查 AudioSessionStore.getByCallId，BYE 处理同样检查音频会话
- 新建 `AudioController`，提供 REST 接口：
  - `POST /api/devices/local/{gbDeviceId}/audio/broadcast/start`
  - `POST /api/devices/local/{gbDeviceId}/audio/broadcast/stop`
  - `POST /api/devices/local/{gbDeviceId}/audio/talk/start`
  - `POST /api/devices/local/{gbDeviceId}/audio/talk/stop`
  - `POST /api/devices/remote/{deviceId}/audio/broadcast/start`
  - `POST /api/devices/remote/{deviceId}/audio/broadcast/stop`
  - `POST /api/devices/remote/{deviceId}/audio/talk/start`
  - `POST /api/devices/remote/{deviceId}/audio/talk/stop`

## Capabilities

### New Capabilities

- `voice-audio-session`: 语音音频会话能力——通过 SIP INVITE 向本端/外域设备发起语音广播（sendonly，s=Broadcast）或语音对讲（sendrecv，s=Talk），经 ZLMediaKit 完成 RTP 媒体中继，REST 接口控制启停

### Modified Capabilities

- `sip-response-routing`（GbtSipListener）：扩展 INVITE 200 OK 和 BYE 分发链，增加音频会话检查

## Impact

- 新增后端文件：`AudioSession.java`、`AudioSessionStore.java`、`SipAudioService.java`、`AudioController.java`
- 修改：`GbtSipListener.java`（INVITE 200 OK 路由、BYE 清理）
- 依赖：现有 `SipMessageSender`、`ZLMediaKitClient`、`ZlmConfig`、`InterconnectConfigRepository`、`RemoteDeviceRepository`、`Ivs1900CameraMappingRepository`
- 无数据库变更，无前端变更（本 change 只做后端信令层）

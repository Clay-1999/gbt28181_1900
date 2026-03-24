## 1. 数据结构

- [x] 1.1 新建 `PlaybackSession.java`（record）：`deviceId`、`callId`、`startTime`、`endTime`、`zlmPort`、`ssrc`、`streamUrl`
- [x] 1.2 新建 `PlaybackSessionStore.java`：`ConcurrentHashMap<String, PlaybackSession>`，提供 `put/get/remove/getByCallId`

## 2. DTO

- [x] 2.1 新建 `PlaybackRequest.java`：`startTime`(String)、`endTime`(String)
- [x] 2.2 新建 `PlaybackControlRequest.java`：`action`(String)、`scale`(Double)、`seekTime`(String)

## 3. SipMessageSender 扩展

- [x] 3.1 新增 `sendInfo(InterconnectConfig config, String deviceId, PlaybackSession session, String body)` 方法：构造 SIP INFO 请求，Content-Type=Application/MANSRTSP，发送

## 4. SipPlaybackService（核心）

- [x] 4.1 新建 `SipPlaybackService.java`，注入 `SipMessageSender`、`PlaybackSessionStore`、`ZLMediaKitClient`、`ZlmConfig`、`RemoteDeviceRepository`、`Ivs1900CameraMappingRepository`、`InterconnectConfigRepository`、`Ivs1900InterconnectConfigRepository`
- [x] 4.2 实现 `startPlayback(type, deviceId, PlaybackRequest)`：分配 ZLM RTP 端口，构造 `s=Playback` SDP（含 `t=` 时间范围、`y=1xxxxxxxxx` SSRC），调 `sipMessageSender.sendInvite()`，等待 10s 200 OK，存入 `PlaybackSessionStore`，返回流 URL
- [x] 4.3 实现 `stopPlayback(type, deviceId)`：查找会话，发送 BYE，移除会话
- [x] 4.4 实现 `controlPlayback(type, deviceId, PlaybackControlRequest)`：查找会话，根据 action 构造 MANSRTSP body，调 `sipMessageSender.sendInfo()`
- [x] 4.5 实现 `onInviteOk(ResponseEvent, callId)`：供 `GbtSipListener` 回调，complete pendingInvites future

## 5. GbtSipListener 扩展

- [x] 5.1 在 `processResponse` 中：INVITE 200 OK 时，先查 `PlaybackSessionStore.getByCallId()`，若存在则调 `sipPlaybackService.onInviteOk()`，否则调原 `sipInviteService.onInviteOk()`
- [x] 5.2 在 `processRequest` 中：收到 BYE 时，同时检查 `PlaybackSessionStore` 并移除会话（不影响实时流 BYE 处理）

## 6. PlaybackController

- [x] 6.1 新建 `PlaybackController.java`，路径 `/api/devices`
- [x] 6.2 实现 `POST /{type}/{deviceId}/playback/start`：调 `sipPlaybackService.startPlayback()`，404/504/200 处理
- [x] 6.3 实现 `POST /{type}/{deviceId}/playback/stop`：调 `sipPlaybackService.stopPlayback()`
- [x] 6.4 实现 `POST /{type}/{deviceId}/playback/control`：调 `sipPlaybackService.controlPlayback()`，404/200 处理

## Why

GB/T 28181-2022 §9.8 历史视音频回放是平台的核心功能之一，目前仅实现了录像文件查询（§9.7）但未实现回放本身。需要通过 INVITE(s=Playback) + SIP INFO（MANSRTSP）实现回放会话的建立、控制（暂停/继续/快进/定位）和终止。

## What Changes

- 新增 `PlaybackSession` 实体和 `PlaybackSessionStore`：管理回放会话状态（deviceId、callId、startTime、endTime）
- 新增 `SipPlaybackService`：发送 INVITE(s=Playback)、发送 INFO MANSRTSP 控制命令、发送 BYE
- 新增 `PlaybackController`：北向 REST 接口（start/stop/control）
- 新增 DTO：`PlaybackRequest`（时间范围）、`PlaybackControlRequest`（Scale/Range）
- 扩展 `GbtSipListener`：处理回放会话的 200 OK 和 BYE
- 扩展 `SipMessageSender`：`sendInfo()` 方法（发送 MANSRTSP）

## Capabilities

### New Capabilities

- `playback-session`: 历史回放会话管理——INVITE(s=Playback)建立、INFO MANSRTSP 控制（暂停/快进/定位）、BYE 终止，以及回放会话状态存储

### Modified Capabilities

- `sip-server-registration`: 无需修改（注册流程不变）

## Impact

- 新增文件：`PlaybackSession.java`、`PlaybackSessionStore.java`、`SipPlaybackService.java`、`PlaybackController.java`、`PlaybackRequest.java`、`PlaybackControlRequest.java`
- 修改文件：`SipMessageSender.java`（+`sendInfo()`）、`GbtSipListener.java`（+回放会话 INVITE 200 OK 处理）
- 依赖：ZLMediaKit 接收 RTP 流（与实时流共用 RTP 端口管理）
- REST 路径：`/api/devices/{type}/{deviceId}/playback/start`、`/stop`、`/control`

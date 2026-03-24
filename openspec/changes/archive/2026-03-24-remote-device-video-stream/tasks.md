## 1. 配置与依赖

- [x] 1.1 在 `application.yml` 新增 ZLMediaKit 配置项：`zlm.base-url`、`zlm.rtp-ip`、`zlm.http-port`、`zlm.secret`
- [x] 1.2 新建 `ZlmConfig.java`（`@ConfigurationProperties("zlm")`），绑定上述配置

## 2. ZLMediaKit HTTP 客户端

- [x] 2.1 新建 `ZLMediaKitClient.java`，注入 `RestTemplate`，实现 `openRtpServer(streamId)` → 返回端口号
- [x] 2.2 在 `ZLMediaKitClient` 实现 `closeRtpServer(streamId)` 方法
- [x] 2.3 在 `ZLMediaKitClient` 实现 `buildFlvUrl(streamId)` → 返回 HTTP-FLV 播放地址

## 3. 流会话存储

- [x] 3.1 新建 `StreamSession.java`（record/class），字段：deviceId、callId、fromTag、toTag、cseq、streamId、zlmPort
- [x] 3.2 新建 `StreamSessionStore.java`（`@Component`），内部 `ConcurrentHashMap<String, StreamSession>`，提供 put/get/remove/has 方法

## 4. SIP INVITE/ACK/BYE 实现

- [x] 4.1 在 `SipMessageSender` 新增 `sendInvite(InterconnectConfig target, String deviceId, String callId, String sdpBody)` → 返回 `ClientTransaction`
- [x] 4.2 在 `SipMessageSender` 新增 `sendAck(ResponseEvent event, String callId, String fromTag, String toTag)` 方法
- [x] 4.3 在 `SipMessageSender` 新增 `sendBye(StreamSession session, InterconnectConfig config)` 方法
- [x] 4.4 在 `GbtSipListener.processResponse` 中处理 INVITE 200 OK：调用 `sendAck`，完成 `SipInviteService` 中等待的 CompletableFuture
- [x] 4.5 在 `GbtSipListener.processRequest` 中处理入站 BYE：回复 200 OK，通知 `SipInviteService` 清理会话
- [x] 4.6 在 `SipStackManager.doStart` 中注入并初始化 `SipInviteService`

## 5. SipInviteService

- [x] 5.1 新建 `SipInviteService.java`（`@Component`），注入 `SipMessageSender`、`StreamSessionStore`、`ZLMediaKitClient`、`InterconnectConfigRepository`、`RemoteDeviceRepository`
- [x] 5.2 实现 `startStream(String deviceId)` → 查找设备和互联配置，调用 ZLM openRtpServer，构造 SDP offer，发送 INVITE，等待 200 OK（10s），发送 ACK，存储会话，返回 FLV URL
- [x] 5.3 实现 `stopStream(String deviceId)` → 查找会话，发送 BYE，调用 ZLM closeRtpServer，移除会话
- [x] 5.4 实现 `onRemoteBye(String callId)` → 按 callId 查找会话，清理 ZLM 资源，移除会话

## 6. REST 接口

- [x] 6.1 新建 `StreamController.java`（`@RestController`，路径 `/api/devices/remote`），注入 `SipInviteService`
- [x] 6.2 实现 `POST /{deviceId}/stream/start` → 调用 `sipInviteService.startStream`，返回 `{"streamUrl": "..."}` 或错误
- [x] 6.3 实现 `POST /{deviceId}/stream/stop` → 调用 `sipInviteService.stopStream`，返回 `{"success": true}` 或 404

## 7. 前端播放界面

- [x] 7.1 在 `frontend/package.json` 添加 `flv.js` 依赖
- [x] 7.2 在 `DevicesView.vue` 外域设备表格操作列新增"播放"按钮
- [x] 7.3 新增播放对话框（`el-dialog`），包含 `<video>` 标签，打开时调用 start 接口，关闭时调用 stop 接口
- [x] 7.4 使用 flv.js 初始化播放器，绑定 HTTP-FLV 流地址，对话框关闭时销毁播放器实例

## 8. 验证

- [x] 8.1 启动 ZLMediaKit，配置 `application.yml`，验证 `ZLMediaKitClient.openRtpServer` 返回端口
- [x] 8.2 调用 `POST /api/devices/remote/{deviceId}/stream/start`，观察 SIP INVITE/200 OK/ACK 日志，验证返回 FLV URL
- [ ] 8.3 前端点击"播放"，验证视频正常显示；点击关闭，验证 BYE 发送
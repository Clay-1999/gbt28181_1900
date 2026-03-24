## Why

当前平台可以管理外域设备目录，但无法在 Web 界面直接预览外域设备的实时视频流。运维人员需要通过第三方工具才能查看视频，效率低下。

## What Changes

- 新增 `POST /api/devices/remote/{deviceId}/stream/start` 接口：通过 GB/T 28181 SIP INVITE 向外域平台发起实时视频流请求，返回可播放的 HTTP-FLV 流地址
- 新增 `POST /api/devices/remote/{deviceId}/stream/stop` 接口：通过 SIP BYE 停止视频流，释放资源
- 新增 SIP INVITE/BYE 会话管理：维护 deviceId → SIP dialog（Call-ID、From-tag、To-tag、CSeq）映射
- 集成 ZLMediaKit 流媒体服务器：后端通过 ZLMediaKit HTTP API 创建 RTP 收流端口，将 RTP 流转为 HTTP-FLV 供前端播放
- 前端 DevicesView.vue 外域设备列表新增"播放"按钮，点击弹出播放对话框，使用 flv.js 播放 HTTP-FLV 流

## Capabilities

### New Capabilities

- `remote-device-stream`: 外域设备实时视频流拉取与播放，包含 SIP INVITE/BYE 流程、ZLMediaKit 集成、流会话生命周期管理
- `zlmediakit-integration`: ZLMediaKit HTTP API 客户端封装，支持创建 RTP 收流端口、查询流状态、删除流

### Modified Capabilities

- `remote-device-api`: 新增 stream/start 和 stream/stop 两个端点

## Impact

- 新增后端类：`SipInviteService`、`StreamSessionStore`、`ZLMediaKitClient`、`StreamController`
- 修改：`DevicesView.vue`（新增播放按钮和播放对话框）
- 新增依赖：flv.js（前端）
- 外部依赖：ZLMediaKit 流媒体服务器（需独立部署，通过 HTTP API 交互）
- SIP 栈需支持 INVITE 事务（当前仅支持 MESSAGE/REGISTER）

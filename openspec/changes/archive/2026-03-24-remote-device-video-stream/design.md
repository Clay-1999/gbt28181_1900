## Context

当前 SIP 栈仅处理 MESSAGE/REGISTER/SUBSCRIBE/NOTIFY 方法，不支持 INVITE 事务。外域设备视频流拉取需要完整的 GB/T 28181 SIP INVITE 流程：本端作为 UAC 发起 INVITE，携带 SDP offer（含 SSRC），对端回 200 OK 携带 SDP answer（含媒体地址），本端回 ACK 完成三次握手，随后 RTP 流推送到本端指定端口。

前端无法直接播放 RTP 流，需要流媒体服务器（ZLMediaKit）将 RTP 转为 HTTP-FLV 供浏览器播放。

## Goals / Non-Goals

**Goals:**
- 实现 GB/T 28181 SIP INVITE/ACK/BYE 完整流程，拉取外域设备实时视频流
- 集成 ZLMediaKit：通过其 HTTP API 创建 RTP 收流端口，获取 HTTP-FLV 播放地址
- 新增 REST 接口 start/stop，前端通过接口控制流会话
- 前端播放对话框，使用 flv.js 播放 HTTP-FLV 流

**Non-Goals:**
- 不支持录像回放（INVITE with time range）
- 不支持 PTZ 控制
- 不支持本端设备（IVS1900）的视频流（已有独立通道）
- 不实现流媒体服务器本身，ZLMediaKit 需独立部署

## Decisions

### 决策 1：使用 ZLMediaKit 作为流媒体中转

**选择**：ZLMediaKit（而非 SRS）

**理由**：ZLMediaKit 原生支持 GB28181 RTP 收流（`/index/api/openRtpServer`），HTTP API 简洁，支持 HTTP-FLV 输出，社区活跃，Java 集成简单。SRS 也支持但 GB28181 集成需要额外配置。

**替代方案**：SRS — 功能相近但 GB28181 支持不如 ZLMediaKit 成熟。

### 决策 2：SSRC 分配策略

**选择**：本端生成随机 SSRC（10位数字），在 SDP offer 的 `y=` 字段携带，同时作为 ZLMediaKit 的 stream_id 前缀。

**理由**：GB/T 28181 要求 SDP 中携带 `y=` 字段标识 SSRC，ZLMediaKit 可按 SSRC 区分不同流。

### 决策 3：SIP INVITE 实现方式

**选择**：在 `SipMessageSender` 中新增 `sendInvite` 方法，在 `GbtSipListener.processResponse` 中处理 INVITE 的 200 OK，发送 ACK。

**理由**：复用已有的 SipProvider 和 Header 工厂，保持架构一致性。INVITE 会话状态由新建的 `StreamSessionStore` 维护。

**替代方案**：开启 JAIN-SIP 的 `AUTOMATIC_DIALOG_SUPPORT`，但当前已关闭（为支持 NOTIFY），改回会影响现有流程。

### 决策 4：RTP 接收端口分配

**选择**：由 ZLMediaKit `openRtpServer` 接口返回分配的端口，本端在 SDP offer 中填写 ZLMediaKit 所在主机的 IP 和该端口。

**理由**：ZLMediaKit 负责接收 RTP，本端 Java 进程不需要监听 RTP 端口，简化实现。

### 决策 5：流会话存储

**选择**：内存 `ConcurrentHashMap`（`StreamSessionStore`），key = deviceId，value = 会话信息（Call-ID、From-tag、To-tag、CSeq、ZLM stream key）。

**理由**：流会话是运行时状态，重启后需重新建立，无需持久化。

## Risks / Trade-offs

- [ZLMediaKit 未部署] → 接口返回错误，前端提示"流媒体服务器不可用"；通过 `application.yml` 配置 ZLM 地址，未配置时跳过
- [对端不回 200 OK] → INVITE 超时（10s），返回 503；`StreamSessionStore` 清理对应 future
- [ACK 发送时机] → JAIN-SIP 关闭了 dialog 支持，需手动构造 ACK；从 200 OK 的 To-tag 提取并构造
- [并发播放同一设备] → 同一 deviceId 已有会话时，先 BYE 旧会话再建新会话

## Migration Plan

1. 部署 ZLMediaKit，配置 `application.yml` 中 `zlm.base-url`
2. 启动后端，验证 `/api/devices/remote/{deviceId}/stream/start` 接口
3. 前端新增播放按钮，无需修改现有功能

## Open Questions

- ZLMediaKit 与本端 Java 服务是否在同一主机？→ 通过配置 `zlm.rtp-ip` 指定 ZLM 接收 RTP 的 IP，默认与 `zlm.base-url` 同主机

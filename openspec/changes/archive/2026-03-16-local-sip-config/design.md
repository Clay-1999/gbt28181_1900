## Context

Phase 1 骨架已完成：`SipStackManager` 仅打印日志，控制器返回占位响应，前端为空白页面。Phase 2 在此基础上填充真实业务逻辑。现有代码结构稳定，包路径、实体、Repository 均已就绪，无需调整。

JAIN-SIP 坐标：`javax.sip:jain-sip-ri:1.2.327`（已在 pom.xml）。

## Goals / Non-Goals

**Goals:**
- `LocalSipConfigService`：封装 `local_sip_config` 单行读写，触发 `SipStackManager.reload()`
- `SipStackManager`：实现 JAIN-SIP `SipStack`/`SipProvider` 真实初始化与热重载（5 步流程）
- `InterconnectConfigService`：封装 `interconnect_config` CRUD
- 控制器填充：`LocalSipConfigController`、`InterconnectConfigController` 调用对应 Service
- 前端：本端配置表单页（轮询状态）、互联管理列表页（弹窗 CRUD）

**Non-Goals:**
- SIP 实际消息收发（REGISTER、SUBSCRIBE、NOTIFY）—— Phase 3
- ivs1900 集成
- 媒体代理

## Decisions

### 1. Service 层封装

引入 `LocalSipConfigService` 和 `InterconnectConfigService`，控制器只做 DTO 转换和 HTTP 响应，业务逻辑全在 Service。

理由：热重载涉及状态机协调（SipStackManager + DB 写入），逻辑放 Service 便于后续 Phase 3 复用。

### 2. SipStackManager 热重载 5 步流程

```
[1] status → RELOADING
[2] 通知所有 InterconnectConfig（enabled=true）的客户端注销（骨架：仅打印，Phase 3 填充实际 SIP 注销）
[3] 销毁旧 SipStack（若存在）
[4] 检查新端口可用性（尝试 DatagramSocket 绑定）
    → 失败：status=ERROR，终止
[5] 用新参数创建 SipStack + SipProvider + ListeningPoint
    → 失败：status=ERROR，终止
[6] 触发所有客户端重新注册（骨架：仅打印，Phase 3 填充）
[7] status → RUNNING
```

`synchronized` 防并发重入；PUT 时若 status==RELOADING 直接返回 409。

### 3. LocalSipConfig 单行保证

`LocalSipConfig.id` 固定为 `1L`，Service 层用 `save()` 做 upsert，不允许创建第二行。

### 4. 前端状态轮询

`PUT /api/local-config` 返回 202 后，前端立即开始每 5 秒轮询 `GET /api/local-config/status`，直到 status 变为 `RUNNING` 或 `ERROR` 为止，最多轮询 12 次（60 秒超时）。

### 5. 互联配置删除时的清理

Phase 2 中删除互联配置仅从 DB 删除，SIP 注销留 Phase 3。前端删除时弹出确认对话框。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| JAIN-SIP SipStack 销毁后端口释放有 TIME_WAIT 延迟 | 销毁后等待 500ms 再重建；或切换端口时不等待 |
| 热重载失败后 SipStack 处于未知状态 | catch 所有异常，确保 status 一定落到 ERROR，errorMsg 有明确描述 |
| 前端轮询期间用户再次提交配置 | PUT 返回 409，前端展示"正在重载中"提示，禁用提交按钮 |

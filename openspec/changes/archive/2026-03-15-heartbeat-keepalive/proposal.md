## Why

Phase 3 实现了基于 REGISTER 续约的保活机制，但续约周期长达 40 分钟（3600s × 2/3），无法快速检测对端离线。GB/T 28181-2022 要求平台间通过 SIP MESSAGE 心跳（CmdType: Keepalive）实现分钟级存活检测，并与注册续约联动——任意一个失败均触发重新注册，确保连接状态的准确性。

## What Changes

- **新增** Client 心跳定时器：注册成功后每 60s 向对端发送 `MESSAGE Keepalive`，连续 3 次无响应则触发重新注册
- **新增** Client 续约定时器：`expires × 2/3` 时发送 `REGISTER refresh`，失败则触发重新注册（原 Phase 3 的续约逻辑重构为独立定时器）
- **新增** 双定时器联动：任意一个失败互相取消对方，共同进入指数退避重新注册流程
- **新增** Server 心跳接收：`GbtSipListener` 处理 `MESSAGE` 请求，`SipRegistrationServer` 更新 `lastHeartbeatAt`，回复 200 OK
- **新增** Server 双重扫描：`@Scheduled` 每 30s 检查注册到期 OR 心跳超时（180s），任意触发则置 `upLinkStatus=OFFLINE`
- **新增** `interconnect_config.last_heartbeat_at` 字段，持久化最后心跳时间
- **新增** 前端互联列表「最后心跳」列

## Capabilities

### New Capabilities

- `sip-client-keepalive`: Client 侧双定时器保活——心跳发送、续约刷新、失败联动重注册
- `sip-server-keepalive`: Server 侧心跳接收、lastHeartbeatAt 更新、双重超时扫描
- `interconnect-heartbeat-status`: interconnect_config 新增 lastHeartbeatAt 字段及前端展示

### Modified Capabilities

- `sip-client-registration`: 原续约逻辑（scheduleRegister at expires×2/3）重构为独立续约定时器，与心跳定时器联动

## Impact

- `SipRegistrationClient.java`：重构续约调度，新增心跳定时器、失败联动逻辑
- `SipRegistrationServer.java`：新增 MESSAGE 处理、lastHeartbeatAt 更新、双重扫描条件
- `GbtSipListener.java`：`processRequest()` 新增 MESSAGE 分发
- `InterconnectConfig.java`：新增 `lastHeartbeatAt` 字段
- `InterconnectConfigResponse.java`：新增 `lastHeartbeatAt` 字段
- `InterconnectsView.vue`：新增「最后心跳」列

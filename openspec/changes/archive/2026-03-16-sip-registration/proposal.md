## Why

Phase 2 完成了本端 SIP 参数配置和 JAIN-SIP Stack 的启动，但 SIP Stack 目前仅绑定端口、无任何消息处理逻辑。互联功能的核心是双向 SIP 注册：本端接受对端平台的 REGISTER（上联），同时向对端主动发起 REGISTER（下联）。没有注册，后续的设备目录同步、命令下发和媒体协商都无从进行。

## What Changes

- **新增** SIP Server 注册处理器：监听 JAIN-SIP 的 REGISTER 请求，实现 RFC 3261 Digest 认证（401 挑战→收到带凭证的 REGISTER→验证→200 OK），维护内存注册表
- **新增** SIP Client 注册管理器：对每条 `enabled=true` 的 `InterconnectConfig` 发起 REGISTER，处理 Digest 挑战，心跳续约，失败时指数退避重试
- **修改** `InterconnectConfig` 实体：增加 `upLinkStatus`（对端是否向本端注册）、`downLinkStatus`（本端是否向对端注册成功）字段
- **修改** `SipStackManager`：热重载时驱动 Client 重新注册，并将真实 `SipListener` 替换掉 Phase 2 的 `NopSipListener`
- **修改** 前端互联管理列表：展示上联/下联状态列

## Capabilities

### New Capabilities

- `sip-server-registration`: 本端作为 SIP Server，接受对端 REGISTER，Digest 401 挑战认证，维护内存注册表，注册到期自动清除
- `sip-client-registration`: 本端作为 SIP Client，主动向对端发起 REGISTER，Digest 应答，expires 前心跳续约，失败指数退避重试

### Modified Capabilities

- `interconnect-config`: InterconnectConfig 增加 upLinkStatus / downLinkStatus 状态字段，API 响应和前端列表同步展示

## Impact

- **实体层**：`InterconnectConfig` 新增两个 enum 字段，JPA ddl-auto:update 自动建列
- **SIP 层**：`SipStackManager` 注入 `SipRegistrationServer` 和 `SipRegistrationClient`，热重载时重启两者
- **API**：`InterconnectConfigResponse` 新增 `upLinkStatus` / `downLinkStatus`
- **前端**：`InterconnectsView.vue` 列表增加两列状态展示
- **依赖**：无新增 Maven 依赖（复用 jain-sip-ri:1.2.327）

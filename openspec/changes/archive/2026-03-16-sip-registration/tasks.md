## 1. 实体与 DTO 扩展

- [x] 1.1 `InterconnectConfig` 实体新增 `upLinkStatus`（枚举 `ONLINE/OFFLINE`，默认 `OFFLINE`）和 `downLinkStatus`（枚举 `REGISTERING/ONLINE/OFFLINE/ERROR`，默认 `OFFLINE`）字段，新建 `LinkStatus` 枚举类
- [x] 1.2 `InterconnectConfigResponse` 新增 `upLinkStatus` / `downLinkStatus` 字段
- [x] 1.3 `InterconnectConfigController.toResponse()` 映射两个新字段

## 2. Digest 工具类

- [x] 2.1 创建 `DigestAuthUtils`：实现 `calcHa1(username, realm, password)`、`calcHa2(method, uri)`、`calcResponse(ha1, nonce, ha2)` 三个静态方法（MD5，RFC 3261）

## 3. SIP Server 注册处理

- [x] 3.1 创建 `SipRegistrationServer`（`@Component`）：持有 `nonce` 内存 Map（nonce→创建时间），注入 `InterconnectConfigRepository`
- [x] 3.2 实现 `handleRegister(RequestEvent)`：无 Authorization 头 → 返回 401（生成 nonce，写入 WWW-Authenticate）；有 Authorization 头 → 验证 Digest → 通过返回 200 并更新注册表和 `upLinkStatus`，失败返回 403；Expires=0 → 注销
- [x] 3.3 实现 `scheduleExpiryCleanup()`：`@Scheduled` 每 30 秒扫描内存注册表，清理过期条目，更新 `upLinkStatus=OFFLINE`
- [x] 3.4 实现 `shutdown()`：清空内存注册表，将所有 `upLinkStatus` 重置为 `OFFLINE`

## 4. SIP Client 注册管理

- [x] 4.1 创建 `SipRegistrationClient`（`@Component`）：注入 `InterconnectConfigRepository`、`SipProvider`（通过 `SipStackManager` 获取），持有每条配置的注册任务 Map
- [x] 4.2 实现 `startAll()`：对所有 `enabled=true` 的 `InterconnectConfig` 调用 `startRegistration(config)`
- [x] 4.3 实现 `startRegistration(config)`：发送无凭证 REGISTER，`downLinkStatus` 设为 `REGISTERING`，记录 ClientTransaction
- [x] 4.4 实现 `handleResponse(ResponseEvent)`：收到 401 → Digest 应答重发；收到 200 → 更新 `downLinkStatus=ONLINE`，调度续约（expires×2/3 后）；收到 4xx/5xx → 指数退避重试
- [x] 4.5 实现 `stopAll()`：取消所有注册任务，`downLinkStatus` 全部设为 `OFFLINE`
- [x] 4.6 实现 `stopRegistration(id)`：取消单条配置的注册任务，`downLinkStatus=OFFLINE`

## 5. GbtSipListener 分发器

- [x] 5.1 创建 `GbtSipListener`（实现 `SipListener`，`@Component`）：注入 `SipRegistrationServer` 和 `SipRegistrationClient`
- [x] 5.2 实现 `processRequest()`：REGISTER 请求 → 转发给 `SipRegistrationServer.handleRegister()`，其他请求打印警告日志
- [x] 5.3 实现 `processResponse()`：2xx/4xx 响应转发给 `SipRegistrationClient.handleResponse()`；`processTimeout()` 通知 Client 重试

## 6. SipStackManager 集成

- [x] 6.1 `SipStackManager` 注入 `SipRegistrationServer` 和 `SipRegistrationClient`
- [x] 6.2 `doStart()` 中将 `NopSipListener` 替换为 `GbtSipListener`
- [x] 6.3 `reload()` 流程更新：步骤 [2] 调用 `sipRegistrationClient.stopAll()`；步骤 [6] 调用 `sipRegistrationClient.startAll()`
- [x] 6.4 `doStop()` 中调用 `sipRegistrationServer.shutdown()` 和 `sipRegistrationClient.stopAll()`

## 7. InterconnectConfigService 联动

- [x] 7.1 `InterconnectConfigService.create()` 创建时设 `upLinkStatus=OFFLINE`、`downLinkStatus=OFFLINE`；若 SipStack RUNNING 且 `enabled=true` 则调用 `sipRegistrationClient.startRegistration(config)`
- [x] 7.2 `InterconnectConfigService.update()` 若 `enabled` 从 false 变 true 且 SipStack RUNNING，触发 `startRegistration()`；若从 true 变 false，触发 `stopRegistration(id)`
- [x] 7.3 `InterconnectConfigService.delete()` 调用 `sipRegistrationClient.stopRegistration(id)` 再删除

## 8. 前端互联管理列表

- [x] 8.1 `InterconnectsView.vue` 列表新增「上联状态」列（Tag：ONLINE=success/OFFLINE=info）和「下联状态」列（Tag：ONLINE=success/REGISTERING=warning/OFFLINE=info/ERROR=danger）
- [x] 8.2 列表每 10 秒自动刷新一次（`setInterval`），在组件卸载时清除

## 9. 验证

- [x] 9.1 启动应用，`GET /api/interconnects` 响应包含 `upLinkStatus` 和 `downLinkStatus` 字段
- [x] 9.2 新增一条 `enabled=true` 的互联配置（对端不存在），`downLinkStatus` 最终变为 `OFFLINE`（连接失败后退避）
- [x] 9.3 使用 SIP 客户端工具（如 sipsak）向本端发送 REGISTER，验证 401 挑战→认证→200 OK 流程，`upLinkStatus` 变为 `ONLINE`
- [x] 9.4 热重载本端配置，Client 重新触发注册，`downLinkStatus` 经历 `REGISTERING` → `OFFLINE/ONLINE`

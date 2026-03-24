## Why

当 GBT28181 下发配置失败时，日志里能看到 SIP 消息的 Call-ID，但无法通过 Call-ID 找到对应的 IVS1900 HTTP 调用入参和出参。原因是 `DeviceConfigHandler` / `ConfigDownloadHandler` 用 `CompletableFuture.runAsync()` 切换了线程，而 `Ivs1900HttpClient` 的日志没有携带任何 SIP 上下文，导致两段日志无法关联。

## What Changes

- 在 `DeviceCommandRouter.route()` 解析到 Call-ID 后，将其写入 SLF4J MDC（`callId` key）
- `DeviceConfigHandler.handle()` 和 `ConfigDownloadHandler.handle()` 在 `CompletableFuture.runAsync()` 时，显式将 MDC 快照传入子线程并在结束时清理
- `Ivs1900HttpClient` 的 GET/POST/PUT 日志格式不变，MDC 中的 `callId` 由日志框架自动附加（需在 logback pattern 中加 `%X{callId}`）
- 在 `GbtSipListener.processRequest()` 处理完成后清理 MDC，防止线程池复用时污染

## Capabilities

### New Capabilities
- `sip-callid-mdc-propagation`: 将 SIP Call-ID 通过 MDC 传播到异步线程，使 IVS1900 HTTP 日志可关联到触发它的 SIP 请求

### Modified Capabilities
（无行为规范层面的变更）

## Impact

- **修改文件**:
  - `GbtSipListener.java` — 入口处写入/清理 MDC
  - `DeviceCommandRouter.java` — 解析 Call-ID 并写入 MDC
  - `DeviceConfigHandler.java` — 异步线程继承 MDC
  - `ConfigDownloadHandler.java` — 异步线程继承 MDC
  - `src/main/resources/application.yml` 或 `logback-spring.xml` — pattern 加 `%X{callId}`
- **不修改**: `Ivs1900HttpClient`、`Ivs1900DeviceConfigClient`（日志自动继承 MDC）
- **无新依赖**: SLF4J MDC 已在 classpath 中

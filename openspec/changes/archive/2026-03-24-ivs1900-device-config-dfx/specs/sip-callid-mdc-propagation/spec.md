## ADDED Requirements

### Requirement: SIP Call-ID 写入 MDC
系统 SHALL 在处理 ConfigDownload / DeviceConfig SIP MESSAGE 时，将该请求的 SIP Call-ID 以 key `callId` 写入当前线程的 SLF4J MDC。

#### Scenario: 收到 DeviceConfig MESSAGE
- **WHEN** `DeviceCommandRouter.route()` 解析到 CmdType 为 `DeviceConfig` 或 `ConfigDownload`
- **THEN** 在路由到 handler 之前，将 SIP Call-ID header 的值写入 `MDC.put("callId", callId)`

#### Scenario: Call-ID header 缺失
- **WHEN** SIP 请求中不含 Call-ID header
- **THEN** 不写入 MDC，继续正常处理，不抛出异常

---

### Requirement: MDC 传播到异步处理线程
系统 SHALL 在 `CompletableFuture.runAsync()` 启动的子线程中恢复父线程的 MDC 快照，使子线程内所有日志自动携带 `callId`。

#### Scenario: 异步线程正常完成
- **WHEN** `DeviceConfigHandler` 或 `ConfigDownloadHandler` 的异步任务执行完毕
- **THEN** 子线程的 MDC 被清理（`MDC.clear()`），不污染线程池中的后续任务

#### Scenario: 异步线程抛出异常
- **WHEN** 异步任务执行过程中抛出未捕获异常
- **THEN** finally 块仍执行 `MDC.clear()`，MDC 不泄漏

---

### Requirement: IVS1900 HTTP 日志携带 Call-ID
系统 SHALL 在日志输出 pattern 中包含 MDC 的 `callId` 字段，使 `Ivs1900HttpClient` 的每条 HTTP 入参/出参日志都可关联到触发它的 SIP Call-ID。

#### Scenario: 正常配置下发触发 HTTP 调用
- **WHEN** `Ivs1900HttpClient` 打印 `[IVS1900] >>> POST ...` 或 `[IVS1900] <<< ...` 日志
- **THEN** 日志行中包含 `[callId]` 字段，值与触发该调用的 SIP MESSAGE 的 Call-ID 一致

#### Scenario: 非 SIP 触发的 HTTP 调用（如定时同步）
- **WHEN** `Ivs1900HttpClient` 在没有 SIP 上下文的线程中被调用
- **THEN** 日志行中 `callId` 字段为空，不影响日志输出

---

### Requirement: 主线程 MDC 清理
系统 SHALL 在 `GbtSipListener.processRequest()` 返回前清理主线程 MDC，防止线程池复用时 callId 污染后续请求。

#### Scenario: 处理完成后线程归还线程池
- **WHEN** `GbtSipListener.processRequest()` 执行完毕
- **THEN** 调用 `MDC.remove("callId")`，主线程 MDC 中不再含有 callId

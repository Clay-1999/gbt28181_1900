## Context

调用链：`GbtSipListener → DeviceCommandRouter → DeviceConfigHandler/ConfigDownloadHandler → Ivs1900DeviceConfigClient → Ivs1900HttpClient`

关键问题：`handle()` 用 `CompletableFuture.runAsync()` 切换线程，SIP Call-ID 没有传入子线程，导致 `Ivs1900HttpClient` 的 HTTP 入参/出参日志与 SIP 请求日志无法关联。

SLF4J MDC（Mapped Diagnostic Context）是标准解法：在线程本地存储中放入 `callId`，日志框架在每条日志输出时自动附加，异步线程需手动传递 MDC 快照。

## Goals / Non-Goals

**Goals:**
- 每条 IVS1900 HTTP 日志（`[IVS1900] >>> POST ...`、`[IVS1900] <<< ...`）都携带触发它的 SIP Call-ID
- 通过 Call-ID 可以在日志中完整还原：收到哪个 SIP 请求 → 调用了哪些 IVS1900 接口 → 入参/出参是什么 → 最终结果
- MDC 在线程结束后清理，不污染线程池

**Non-Goals:**
- 不引入分布式 tracing 框架（Zipkin/Jaeger）
- 不修改 `Ivs1900HttpClient` 内部逻辑
- 不改变任何 public 方法签名

## Decisions

### 1. MDC 写入点：DeviceCommandRouter，不在 GbtSipListener

**选择**：在 `DeviceCommandRouter.route()` 解析 XML 拿到 Call-ID 后写入 MDC；`GbtSipListener.processRequest()` 在调用完 `route()` 后清理 MDC。

**理由**：Call-ID 在 SIP header 里，`GbtSipListener` 可以直接拿到，但 MDC 的语义是"当前处理上下文"，写在 router 层更贴近业务。`GbtSipListener` 负责清理，保证主线程 MDC 干净。

**替代方案**：在 `GbtSipListener` 写入 — 更早，但 `GbtSipListener` 处理 REGISTER/SUBSCRIBE 等不需要 Call-ID 关联的请求时也会写入，略显冗余。

---

### 2. 异步线程 MDC 传递：手动快照，不用自定义 Executor

**选择**：在 `CompletableFuture.runAsync()` 的 lambda 入口处，用 `MDC.getCopyOfContextMap()` 获取快照，在子线程内 `MDC.setContextMap()` 恢复，finally 块中 `MDC.clear()`。

```java
Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
CompletableFuture.runAsync(() -> {
    if (mdcSnapshot != null) MDC.setContextMap(mdcSnapshot);
    try {
        // ... 业务逻辑
    } finally {
        MDC.clear();
    }
});
```

**理由**：零依赖，代码意图直观，只影响两个 handler，改动范围最小。

**替代方案**：自定义 `MdcAwareExecutor` 包装线程池 — 更通用，但引入新基础设施，超出本次范围。

---

### 3. 日志 pattern：在 logback 配置中加 `%X{callId}`

**选择**：在 `application.yml` 的 `logging.pattern.console` 中加入 `[%X{callId}]`，无 callId 时输出空字符串。

**理由**：一处配置，所有日志自动携带，不需要每个 logger 手动加参数。

## Risks / Trade-offs

- **MDC 泄漏**：若 finally 块未执行（JVM crash 等极端情况），MDC 残留在线程池线程上 → 影响极小，下次请求会覆盖
- **Call-ID 为空**：非 MESSAGE 类请求（REGISTER、SUBSCRIBE）不经过 router，MDC 中无 callId → 日志输出 `[]`，不影响功能
- **`MDC.getCopyOfContextMap()` 返回 null**：MDC 从未设置时返回 null，需做 null 判断 → 已在方案中处理

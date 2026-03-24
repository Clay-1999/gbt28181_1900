## Source: sip-stack-init

## ADDED Requirements

### Requirement: 应用启动时初始化 SIP Stack

系统 SHALL 在 `ApplicationReadyEvent` 后，从 `local_sip_config` 读取参数，尝试初始化 JAIN-SIP Stack。

#### Scenario: 配置完整，初始化成功

- **WHEN** 应用启动，`local_sip_config` 中 `deviceId` 不为 null，端口可用
- **THEN** JAIN-SIP `SipStack` + `SipProvider` + `ListeningPoint` 创建成功，`status=RUNNING`，日志输出本端 SIP ID 和监听地址

#### Scenario: 配置缺失，跳过初始化

- **WHEN** 应用启动，`local_sip_config.deviceId` 为 null（未配置）
- **THEN** 不尝试初始化，`status=ERROR`，`errorMsg="本端 SIP 参数未配置"`

#### Scenario: 端口被占用，初始化失败

- **WHEN** 应用启动，配置端口已被占用
- **THEN** `status=ERROR`，`errorMsg` 含端口冲突描述，应用其余功能正常启动

---

### Requirement: 热重载 SIP Stack

系统 SHALL 在收到新本端配置后，按 5 步流程热重载 JAIN-SIP Stack，全程不重启 JVM 进程。

#### Scenario: 热重载成功

- **WHEN** `SipStackManager.reload(config)` 被调用，新端口可用
- **THEN** 按序：标记 RELOADING → 触发互联客户端注销占位 → 销毁旧 SipStack → 检查端口 → 创建新 SipStack → 触发客户端重连占位 → 标记 RUNNING

#### Scenario: 新端口不可用

- **WHEN** 热重载步骤 [4] 端口检查失败（端口被占用）
- **THEN** `status=ERROR`，`errorMsg` 含"端口 {port} 不可用"，旧 SipStack 已销毁（不恢复）

#### Scenario: SipStack 创建失败

- **WHEN** 热重载步骤 [5] `SipStack` 创建抛出异常
- **THEN** `status=ERROR`，`errorMsg` 含异常信息

#### Scenario: 并发热重载

- **WHEN** 热重载正在进行（status=RELOADING），再次调用 `reload()`
- **THEN** 立即返回，不执行第二次热重载（`synchronized` 保证）

---

## Source: sip-stack-lifecycle

## Requirements

### Requirement: SIP Stack 热重载
`SipStackManager.reload(config)` SHALL 以原子方式完成以下步骤：
1. 设置状态为 `RELOADING`
2. 停止所有 SIP Client 注册任务（`SipRegistrationClient.stopAll()`）
3. 关闭 SIP 服务端（`SipRegistrationServer.shutdown()`）
4. 销毁旧 `SipStack`（含 500ms 延迟等待栈释放）
5. 检查新端口可用性
6. 用新参数创建 `SipStack`、`ListeningPoint`、`SipProvider`
7. 将 `SipProvider` 注入 `SipRegistrationServer` 和 `SipRegistrationClient`
8. 更新 `LocalSipConfigHolder` 全局配置
9. 启动所有启用的互联配置注册任务（`SipRegistrationClient.startAll()`）
10. 设置状态为 `RUNNING`

#### Scenario: 热重载成功
- **WHEN** 调用 `reload(config)`，新端口可用，SipStack 创建成功
- **THEN** 状态依次经历 `RELOADING → RUNNING`，所有互联客户端重新注册

#### Scenario: 端口不可用
- **WHEN** 新配置的 `sipPort` 被其他进程占用
- **THEN** 状态设置为 `ERROR`，`errorMsg` 记录端口冲突原因，热重载终止，旧 Stack 已销毁

#### Scenario: SipStack 创建失败
- **WHEN** JAIN-SIP 初始化抛出异常
- **THEN** 状态设置为 `ERROR`，`errorMsg` 记录异常消息，热重载终止

---

### Requirement: 端口可用性检查
系统 SHALL 在创建新 SipStack 之前，通过尝试绑定 Socket 验证端口可用性。

#### Scenario: UDP 端口检查
- **WHEN** `transport` 为 `"UDP"`
- **THEN** 尝试绑定 `DatagramSocket(port, ip)`，成功则可用，异常则不可用

#### Scenario: TCP 端口检查
- **WHEN** `transport` 为 `"TCP"`
- **THEN** 尝试绑定 `ServerSocket(port, backlog, ip)`，成功则可用，异常则不可用

---

### Requirement: 并发重入保护
`reload()` 方法 SHALL 为 `synchronized` 方法，防止并发调用导致 SipStack 状态不一致。

#### Scenario: 重载进行中忽略并发请求
- **WHEN** `reload()` 正在执行时，另一线程再次调用 `reload()`
- **THEN** 后续调用因 `synchronized` 阻塞等待，或通过检查 `RELOADING` 状态提前返回

---

### Requirement: 全局 SIP 参数缓存（LocalSipConfigHolder）
系统 SHALL 维护一个线程安全的静态缓存（`volatile` 字段），供 `SipRegistrationClient` 在构造 SIP 请求时快速访问本端参数（`deviceId`、`sipIp`、`sipPort`），无需每次查库。

#### Scenario: 热重载后缓存更新
- **WHEN** `SipStackManager.doStart()` 执行成功
- **THEN** `LocalSipConfigHolder.update(deviceId, sipIp, sipPort)` 被调用，后续所有 SIP 请求使用新参数

---

### Requirement: 应用停止时清理
系统 SHALL 在应用关闭时（`@PreDestroy`）调用 `stop()`，销毁 SipStack 并将所有互联状态重置为 `OFFLINE`。

#### Scenario: 应用正常关闭
- **WHEN** Spring 容器关闭
- **THEN** SipStack 销毁，所有 `interconnect_config.up_link_status` / `down_link_status` 置为 `OFFLINE`

---

## Source: sip-stack-skeleton

## ADDED Requirements

### Requirement: SipStackManager 骨架

系统 SHALL 提供 `SipStackManager` Spring Bean，封装 SIP Stack 生命周期管理，对外暴露状态查询接口。骨架阶段不实现实际 JAIN-SIP 操作。

#### Scenario: 应用启动时 SipStackManager 初始化

- **WHEN** Spring 容器启动完成
- **THEN** `SipStackManager` Bean 被创建，初始状态为 `ERROR`（等待本端配置完善后热重载）

#### Scenario: 获取当前状态

- **WHEN** 调用 `SipStackManager.getStatus()`
- **THEN** 返回当前状态枚举值（`RUNNING` / `RELOADING` / `ERROR`）

#### Scenario: 触发热重载（骨架）

- **WHEN** 调用 `SipStackManager.reload(LocalSipConfig config)`
- **THEN** 状态变更为 `RELOADING`，执行骨架重载逻辑（仅打印日志），完成后状态变更为 `RUNNING`；发生异常时状态变更为 `ERROR` 并记录 errorMsg

---

## Source: sip-callid-mdc-propagation

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

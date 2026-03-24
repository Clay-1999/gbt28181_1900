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

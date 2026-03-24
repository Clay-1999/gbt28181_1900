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

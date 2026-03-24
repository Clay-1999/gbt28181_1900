## Why

Phase 1（framework-scaffold）已建立完整骨架，但控制器和 SipStackManager 均为占位实现。Phase 2 目标是填充实际业务逻辑：将本端 SIP 参数和互联配置持久化到 H2，实现 JAIN-SIP Stack 的真实初始化与热重载，并完成前端配置页面，使系统具备可用的配置管理能力，为 Phase 3 的 SIP 注册功能提供稳定的配置基础。

## What Changes

- **填充** `LocalSipConfigController`：从 `local_sip_config` 表读写，`PUT` 触发热重载返回 202，热重载进行中返回 409
- **填充** `SipStackManager`：实现 JAIN-SIP Stack 真实初始化、热重载完整流程（注销互联客户端 → 销毁旧 Stack → 端口检查 → 重建 Stack → 重启互联客户端）、失败标记 ERROR
- **填充** `InterconnectConfigController`：CRUD 读写 `interconnect_config` 表，新增/修改/删除时联动 SipStackManager 中的客户端占位
- 新增 `LocalSipConfigService`、`InterconnectConfigService` 业务服务层
- **实现** Vue 3 本端配置页：表单展示/编辑 + 保存 + 5 秒轮询 SIP Stack 状态
- **实现** Vue 3 互联管理页：列表 + 新增/编辑弹窗 + 删除确认

## Capabilities

### New Capabilities

- `local-sip-config-service`: 本端 SIP 参数的持久化读写与热重载编排服务
- `interconnect-config-service`: 互联配置 CRUD 业务服务
- `sip-stack-init`: JAIN-SIP Stack 真实初始化与热重载实现
- `config-ui`: Vue 3 本端配置页与互联管理页

### Modified Capabilities

（无已有规格需修改）

## Impact

- 修改文件：`LocalSipConfigController`、`InterconnectConfigController`、`SipStackManager`、`LocalSipConfigInitializer`
- 新增文件：`LocalSipConfigService`、`InterconnectConfigService`
- 修改前端：`LocalConfigView.vue`、`InterconnectsView.vue`
- 依赖：无新增，JAIN-SIP（`javax.sip:jain-sip-ri`）已在 pom.xml 中

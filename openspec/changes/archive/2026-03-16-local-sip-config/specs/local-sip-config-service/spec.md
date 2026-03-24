## ADDED Requirements

### Requirement: 读取本端 SIP 配置

系统 SHALL 从 `local_sip_config` 表读取当前本端 SIP 参数，并将密码脱敏后返回给调用方。

#### Scenario: 正常读取

- **WHEN** 调用 `GET /api/local-config`
- **THEN** 返回 200，body 含 `deviceId`、`domain`、`sipIp`、`sipPort`、`transport`、`username`、`password="***"`、`expires`、`status`、`errorMsg`

#### Scenario: 首次启动无配置

- **WHEN** 数据库中 `local_sip_config` 仅有 `LocalSipConfigInitializer` 写入的默认行（`deviceId` 为 null）
- **THEN** 返回 200，`status=ERROR`，`errorMsg="本端 SIP 参数未配置"`

---

### Requirement: 更新本端 SIP 配置并触发热重载

系统 SHALL 将新本端参数写入 `local_sip_config` 表，并异步触发 `SipStackManager.reload()`，立即返回 202。

#### Scenario: 提交合法配置

- **WHEN** 请求 `PUT /api/local-config` 携带合法参数
- **THEN** 参数写入数据库，异步触发热重载，立即返回 202 Accepted，`status` 字段为 `RELOADING`

#### Scenario: 热重载进行中再次提交

- **WHEN** `SipStackManager.status == RELOADING` 时再次请求 `PUT /api/local-config`
- **THEN** 返回 409 Conflict，body 含提示"SIP Stack 正在重载，请稍后再试"

#### Scenario: 参数校验失败

- **WHEN** 请求 `PUT /api/local-config` 缺少必填字段或格式非法
- **THEN** 返回 400，body 含字段级错误，不触发热重载

## ADDED Requirements

### Requirement: IVS1900 互联参数 CRUD
系统 SHALL 提供 `Ivs1900InterconnectConfig` 实体及完整的 REST CRUD API，用于存储 IVS1900 的 GB/T 28181 SIP 接入参数。

**字段定义：**
- `sipId`：IVS1900 的 GB/T 28181 设备 ID（必填），用于识别其 REGISTER 请求及向其发送 SIP MESSAGE
- `ip`：IVS1900 SIP 监听 IP（必填），用于向其发送 SIP MESSAGE
- `port`：IVS1900 SIP 端口（必填）
- `domain`：IVS1900 SIP 域（必填）
- `password`：Digest 认证密码（存储明文，API 返回时掩码为 `"***"`）
- `upLinkStatus`：上联注册状态（`OFFLINE`/`ONLINE`，只读，由 `SipRegistrationServer` 维护）
- `createdAt`：创建时间（自动设置）

#### Scenario: 创建 IVS1900 互联配置
- **WHEN** 调用 `POST /api/ivs1900/interconnect` 携带有效请求体
- **THEN** 保存配置，初始 `upLinkStatus=OFFLINE`；系统等待 IVS1900 主动发起 REGISTER

#### Scenario: 查询 IVS1900 互联配置
- **WHEN** 调用 `GET /api/ivs1900/interconnect`
- **THEN** 返回当前配置列表（通常为单条），`password` 字段以 `"***"` 掩码返回，含 `upLinkStatus`

#### Scenario: 更新 IVS1900 互联配置
- **WHEN** 调用 `PUT /api/ivs1900/interconnect/{id}` 携带更新字段
- **THEN** 更新 SIP 参数；`upLinkStatus` 不受请求体影响，由系统维护

#### Scenario: 删除 IVS1900 互联配置
- **WHEN** 调用 `DELETE /api/ivs1900/interconnect/{id}`
- **THEN** 删除记录，`upLinkStatus` 清除；不存在时返回 `404 Not Found`

## MODIFIED Requirements

### Requirement: 互联配置 CRUD

系统 SHALL 提供互联配置的完整增删改查，持久化到 `interconnect_config` 表，密码字段透明加密存储，并包含上联/下联注册状态字段。

#### Scenario: 新增互联配置

- **WHEN** 请求 `POST /api/interconnects` 携带合法参数
- **THEN** 写入数据库，`upLinkStatus=OFFLINE`，`downLinkStatus=OFFLINE`，返回 201，body 含新记录完整信息（密码脱敏）

#### Scenario: 查询全部互联配置

- **WHEN** 请求 `GET /api/interconnects`
- **THEN** 返回 200，body 为数组，按 `createdAt` 倒序，每条记录含 `upLinkStatus` 和 `downLinkStatus`，密码字段均显示 `***`

#### Scenario: 查询单条互联配置

- **WHEN** 请求 `GET /api/interconnects/{id}` 且记录存在
- **THEN** 返回 200，body 含该条配置（含两个状态字段，密码脱敏）

#### Scenario: 查询不存在的互联配置

- **WHEN** 请求 `GET /api/interconnects/{id}` 且记录不存在
- **THEN** 返回 404

#### Scenario: 修改互联配置

- **WHEN** 请求 `PUT /api/interconnects/{id}` 携带合法参数
- **THEN** 更新数据库对应记录（状态字段不可通过此接口修改），返回 200，body 含更新后配置

#### Scenario: 删除互联配置

- **WHEN** 请求 `DELETE /api/interconnects/{id}` 且记录存在
- **THEN** 取消该配置对应的 SIP Client 注册任务，从数据库删除，返回 204

#### Scenario: 删除不存在的互联配置

- **WHEN** 请求 `DELETE /api/interconnects/{id}` 且记录不存在
- **THEN** 返回 404

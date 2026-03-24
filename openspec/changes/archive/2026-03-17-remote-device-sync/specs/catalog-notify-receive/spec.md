## ADDED Requirements

### Requirement: 接收并解析外域设备目录 NOTIFY
系统 SHALL 处理对端推送的 `NOTIFY`（Event: Catalog），解析 XML 中的设备列表并 upsert 到 `remote_device` 表。

#### Scenario: 收到有效 Catalog NOTIFY
- **WHEN** 收到对端 `NOTIFY` 请求，`Event: Catalog`，Body 包含合法的 `<DeviceList>`
- **THEN** 回复 `200 OK`，解析每个 `<Item>`，按 `deviceId` upsert 到 `remote_device` 表，`interconnectConfigId` 从 From header SIP ID 反查 `interconnect_config`

#### Scenario: NOTIFY 来源未知
- **WHEN** 收到 `NOTIFY`，但 From header 的 SIP ID 不在 `interconnect_config` 中
- **THEN** 回复 `200 OK`，丢弃设备数据，记录 warn 日志

#### Scenario: NOTIFY Body 为空或解析失败
- **WHEN** 收到 `NOTIFY`，但 Body 为空或 XML 格式非法
- **THEN** 回复 `200 OK`，不写入数据库，记录 error 日志

#### Scenario: 设备列表为空
- **WHEN** 收到 `NOTIFY`，`<DeviceList Num="0">` 或无 `<Item>`
- **THEN** 回复 `200 OK`，不写入数据库

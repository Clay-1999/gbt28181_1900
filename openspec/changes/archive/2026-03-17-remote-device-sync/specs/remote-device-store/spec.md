## ADDED Requirements

### Requirement: 外域设备持久化
系统 SHALL 维护 `remote_device` 表，存储从外域平台同步来的设备信息，每条记录关联到一个互联配置。

#### Scenario: 新设备入库
- **WHEN** 收到外域设备数据（deviceId 不存在于表中）
- **THEN** 插入新记录，包含 `deviceId`、`name`、`status`、`interconnectConfigId`、`syncedAt`

#### Scenario: 已有设备更新
- **WHEN** 收到外域设备数据（deviceId 已存在于表中）
- **THEN** 更新 `name`、`status`、`syncedAt`，`deviceId` 和 `interconnectConfigId` 不变

#### Scenario: 按互联配置查询
- **WHEN** 查询指定 `interconnectConfigId` 的外域设备
- **THEN** 返回该互联配置下所有设备记录

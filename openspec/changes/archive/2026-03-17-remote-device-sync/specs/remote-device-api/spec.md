## ADDED Requirements

### Requirement: 外域设备列表查询 API
系统 SHALL 提供 REST API，供前端查询外域平台同步来的设备列表。

#### Scenario: 查询外域设备列表
- **WHEN** 客户端发送 `GET /api/devices/remote`
- **THEN** 返回 `remote_device` 中所有记录，包含 `deviceId`、`name`、`status`、`interconnectName`（关联互联配置的 name）、`syncedAt`

#### Scenario: 无外域设备时
- **WHEN** `remote_device` 表无数据
- **THEN** 返回空数组 `[]`，HTTP 200

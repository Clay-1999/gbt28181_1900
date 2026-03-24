## ADDED Requirements

### Requirement: 本端设备列表查询 API
系统 SHALL 提供 REST API，供前端查询本端 IVS1900 相机列表。

#### Scenario: 查询设备列表
- **WHEN** 客户端发送 `GET /api/devices`
- **THEN** 返回 `ivs1900_camera_mapping` 中所有记录，包含 `gbDeviceId`、`name`、`status`、`syncedAt` 字段

#### Scenario: 设备列表为空
- **WHEN** `ivs1900_camera_mapping` 表无数据
- **THEN** 返回空数组 `[]`，HTTP 200

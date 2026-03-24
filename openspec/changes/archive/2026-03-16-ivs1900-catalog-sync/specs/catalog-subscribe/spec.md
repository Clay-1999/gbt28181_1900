## ADDED Requirements

### Requirement: 响应对端设备目录订阅
系统 SHALL 响应对端的 `SUBSCRIBE Catalog` 请求，从 `ivs1900_camera_mapping` 表读取本端设备列表，组装 GB/T 28181 XML 发送 `NOTIFY`。

#### Scenario: 对端订阅设备目录
- **WHEN** 收到对端 `SUBSCRIBE` 请求（`Event: Catalog`）
- **THEN** 立即回复 `200 OK`，然后发送 `NOTIFY`，XML Body 包含 `ivs1900_camera_mapping` 中所有记录，`Status` 字段 `ONLINE`→`"ON"`，`OFFLINE`→`"OFF"`

#### Scenario: 映射表为空时响应
- **WHEN** 收到 `SUBSCRIBE Catalog`，但 `ivs1900_camera_mapping` 表无数据
- **THEN** 回复 `200 OK`，发送 `SumNum=0` 的空 `NOTIFY`

#### Scenario: 未知 Event 类型
- **WHEN** 收到 `SUBSCRIBE` 请求，但 `Event` 头不是 `Catalog`
- **THEN** 回复 `489 Bad Event`

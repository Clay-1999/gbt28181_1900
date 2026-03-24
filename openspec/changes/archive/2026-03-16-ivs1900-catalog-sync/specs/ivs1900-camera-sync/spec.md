## ADDED Requirements

### Requirement: IVS1900 相机列表定时同步
系统 SHALL 每 60 秒从 IVS1900 拉取相机列表和在线状态，维护 `ivs1900_camera_mapping` 表。

#### Scenario: 首次同步生成国标 ID
- **WHEN** 从 IVS1900 查询到新相机（`ivsCameraId` 不在映射表中）
- **THEN** 将相机写入 `ivs1900_camera_mapping`，生成国标设备 ID（`domainCode前10位 + "132" + id补零至7位`），`id` 取自自增主键

#### Scenario: 更新在线状态
- **WHEN** 定时同步任务执行
- **THEN** 调用 `GET /device/channelDevInfo`，解析 `isOnline` 字段（字符串 `"true"`/`"false"`），更新映射表的 `status` 字段（`ONLINE`/`OFFLINE`）

#### Scenario: 更新相机名称
- **WHEN** IVS1900 相机名称变更
- **THEN** 更新映射表的 `name` 字段

#### Scenario: IVS1900 不可达时保留已有数据
- **WHEN** 定时同步任务调用 IVS1900 接口失败
- **THEN** 记录错误日志，保留映射表中已有数据不变，不清空设备列表

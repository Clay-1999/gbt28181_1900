## ADDED Requirements

### Requirement: 接收上级 DeviceControl/PTZCmd 并路由执行
系统 SHALL 接受上级平台发来的 `DeviceControl` SIP MESSAGE，解析 `PTZCmd` 字段，按目标 `DeviceID` 路由到本端相机（通过 `PtzService`）或外域设备（通过 `RemoteDeviceMessageForwarder` 转发）。

#### Scenario: 接收本端相机 PTZ 命令
- **WHEN** 收到 SIP MESSAGE，CmdType=`DeviceControl`，DeviceID 为本端 `ivs1900_camera_mapping` 中的 gbDeviceId
- **THEN** 回复 `200 OK`，解析 `PTZCmd` 十六进制字节，调用 `PtzService` 执行对应动作（方向/速度/停止）

#### Scenario: 接收外域设备 PTZ 命令
- **WHEN** 收到 SIP MESSAGE，CmdType=`DeviceControl`，DeviceID 为 `remote_device` 中的设备 ID
- **THEN** 回复 `200 OK`，通过 `RemoteDeviceMessageForwarder` 将 DeviceControl 消息原样转发至对应下级平台

#### Scenario: 目标设备不存在
- **WHEN** 收到 DeviceControl，DeviceID 在本端和外域均无记录
- **THEN** 回复 `404 Not Found`，记录 WARN 日志

#### Scenario: PTZCmd 解析失败
- **WHEN** PTZCmd 字段格式不合法（非16进制或长度不符）
- **THEN** 回复 `200 OK`（避免上级重发），记录 WARN 日志，不执行动作

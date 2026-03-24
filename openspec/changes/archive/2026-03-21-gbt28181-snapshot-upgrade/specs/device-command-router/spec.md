## ADDED Requirements

### Requirement: 路由入站 SnapShotConfig 命令
系统 SHALL 在 `DeviceCommandRouter` 中识别 CmdType=SnapShotConfig 的入站 SIP MESSAGE，先回复 200 OK，再将命令路由到本端或外域设备。

#### Scenario: 入站 SnapShotConfig 路由到本端设备
- **WHEN** 收到 CmdType=SnapShotConfig 的 SIP MESSAGE，DeviceID 属于本端 IVS1900 相机
- **THEN** 回复 200 OK，记录 info 日志（本端 IVS1900 不支持抓拍时静默处理）

#### Scenario: 入站 SnapShotConfig 路由到外域设备
- **WHEN** 收到 CmdType=SnapShotConfig 的 SIP MESSAGE，DeviceID 属于外域注册设备
- **THEN** 回复 200 OK，通过 RemoteDeviceMessageForwarder 透传至对端平台

#### Scenario: 入站 SnapShotConfig 目标设备未知
- **WHEN** 收到 CmdType=SnapShotConfig 的 SIP MESSAGE，DeviceID 不在数据库中
- **THEN** 回复 200 OK，记录 warn 日志

---

### Requirement: 路由入站 DeviceUpgrade 命令
系统 SHALL 在 `DeviceCommandRouter` 中识别 CmdType=DeviceUpgrade 的入站 SIP MESSAGE，先回复 200 OK，再将命令路由到本端或外域设备。

#### Scenario: 入站 DeviceUpgrade 路由到本端设备
- **WHEN** 收到 CmdType=DeviceUpgrade 的 SIP MESSAGE，DeviceID 属于本端 IVS1900 相机
- **THEN** 回复 200 OK，记录 info 日志（含 FirmwareID）

#### Scenario: 入站 DeviceUpgrade 路由到外域设备
- **WHEN** 收到 CmdType=DeviceUpgrade 的 SIP MESSAGE，DeviceID 属于外域注册设备
- **THEN** 回复 200 OK，通过 RemoteDeviceMessageForwarder 透传至对端平台

#### Scenario: 入站 DeviceUpgrade 目标设备未知
- **WHEN** 收到 CmdType=DeviceUpgrade 的 SIP MESSAGE，DeviceID 不在数据库中
- **THEN** 回复 200 OK，记录 warn 日志

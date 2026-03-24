## Why

GB/T 28181-2022 定义的多项基础信令在本平台尚未实现，导致与标准设备互联时功能不完整。本次补全三类低/中复杂度的缺失功能：校时（9.10）、设备信息/状态查询（9.5）、设备控制命令（9.3 DeviceControl/GuardCmd/RecordCmd/TeleBoot），同时兼顾南向（平台作为服务端接收下级设备）和北向（平台作为客户端向上级平台/设备发送命令）两个方向。

## What Changes

- **9.10 校时**：REGISTER 200 OK 响应中加入 `Date` 头域，向下级设备下发当前时间
- **9.5 DeviceInfo 查询**：平台主动向设备发送 `<Query><CmdType>DeviceInfo</CmdType>` MESSAGE，解析响应并持久化；北向提供 REST 接口
- **9.5 DeviceStatus 查询**：平台主动向设备发送 `<Query><CmdType>DeviceStatus</CmdType>` MESSAGE，解析响应；北向提供 REST 接口
- **9.3 DeviceControl**：北向 REST 接口触发 GuardCmd（布/撤防）、RecordCmd（开始/停止录像）、TeleBoot（远程重启）；南向接收并路由这些命令到本端设备或转发到外域

## Capabilities

### New Capabilities
- `time-sync`: 注册响应中携带 Date 头域实现校时（南向）
- `device-info-query`: 主动查询设备信息（DeviceInfo/DeviceStatus），持久化结果，北向 REST 接口
- `device-control-commands`: GuardCmd/RecordCmd/TeleBoot 命令的北向 REST 接口 + 南向 SIP MESSAGE 发送与接收路由

### Modified Capabilities
- `sip-server-registration`: REGISTER 200 OK 中新增 Date 头域

## Impact

- `SipRegistrationServer.java`：200 OK 响应加 Date 头域
- 新建 `DeviceInfoQueryService.java`：发送 DeviceInfo/DeviceStatus 查询，等待响应
- 新建 `DeviceInfoQueryXml.java` / `DeviceStatusQueryXml.java`：XML 序列化/反序列化
- `DeviceController.java`：新增 DeviceInfo/DeviceStatus/GuardCmd/RecordCmd/TeleBoot REST 端点
- `DeviceCommandRouter.java`：新增 GuardCmd/RecordCmd/TeleBoot 入站路由处理
- `RemoteDeviceMessageForwarder.java`：复用现有 sendMessage 发送控制命令到外域设备
- 数据库：`remote_device` / `ivs1900_camera_mapping` 表可能新增 `device_info` 字段（可选）

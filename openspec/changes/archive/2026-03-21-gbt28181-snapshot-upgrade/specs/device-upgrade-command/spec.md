## ADDED Requirements

### Requirement: 北向 REST 触发固件升级（DeviceUpgrade）
系统 SHALL 提供 REST 接口，向本端或外域设备发送 `<Control><CmdType>DeviceUpgrade</CmdType>` SIP MESSAGE，通知设备从指定地址下载并安装固件。

请求体字段：
- `firmwareId`（字符串，必填）：固件版本标识，如 `V5.8.0`
- `firmwareAddr`（字符串，必填）：固件下载 HTTP 地址

SIP MESSAGE 体格式：
```xml
<Control>
  <CmdType>DeviceUpgrade</CmdType>
  <SN>{sn}</SN>
  <DeviceID>{deviceId}</DeviceID>
  <FirmwareID>{firmwareId}</FirmwareID>
  <FirmwareAddr>{firmwareAddr}</FirmwareAddr>
</Control>
```

设备下载并应用固件后重启，随后重新发送 REGISTER，平台通过注册流程感知升级完成。平台不需等待升级结果——命令发出即视为成功发送。

#### Scenario: 向本端设备发送升级命令
- **WHEN** 调用 `POST /api/devices/local/{gbDeviceId}/upgrade`，body 含合法 firmwareId/firmwareAddr
- **THEN** 系统向该设备发送 DeviceUpgrade SIP MESSAGE，返回 `{"sent": true}`

#### Scenario: 向外域设备发送升级命令
- **WHEN** 调用 `POST /api/devices/remote/{deviceId}/upgrade`，body 含合法字段
- **THEN** 系统向该外域设备发送 DeviceUpgrade SIP MESSAGE，返回 `{"sent": true}`

#### Scenario: 设备不存在时返回 404
- **WHEN** 调用升级接口但 deviceId 在数据库中不存在
- **THEN** 返回 `404 Not Found`，body 含 error 字段

#### Scenario: 南向接收 DeviceUpgrade 命令路由
- **WHEN** 收到 CmdType=DeviceUpgrade 的入站 SIP MESSAGE
- **THEN** DeviceCommandRouter 回复 200 OK，并将命令路由到目标本端设备或透传至外域设备

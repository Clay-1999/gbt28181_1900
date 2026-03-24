## ADDED Requirements

### Requirement: 北向 REST 触发图像抓拍（SnapShotConfig）
系统 SHALL 提供 REST 接口，向本端或外域设备发送 `<Control><CmdType>SnapShotConfig</CmdType>` SIP MESSAGE，触发设备执行远程图像抓拍并上传至指定地址。

请求体字段：
- `snapNum`（整数，必填）：抓拍张数，≥1
- `interval`（整数，必填）：抓拍间隔（秒），0 表示连续
- `uploadAddr`（字符串，必填）：设备上传抓拍图片的 HTTP 地址
- `resolution`（字符串，可选）：分辨率，如 `HD720P`、`HD1080P`

SIP MESSAGE 体格式：
```xml
<Control>
  <CmdType>SnapShotConfig</CmdType>
  <SN>{sn}</SN>
  <DeviceID>{deviceId}</DeviceID>
  <SnapNum>{snapNum}</SnapNum>
  <Interval>{interval}</Interval>
  <UploadAddr>{uploadAddr}</UploadAddr>
  <Resolution>{resolution}</Resolution>
</Control>
```

#### Scenario: 向本端设备触发抓拍
- **WHEN** 调用 `POST /api/devices/local/{gbDeviceId}/snapshot`，body 含合法 snapNum/interval/uploadAddr
- **THEN** 系统向该设备发送 SnapShotConfig SIP MESSAGE，返回 `{"sent": true}`

#### Scenario: 向外域设备触发抓拍
- **WHEN** 调用 `POST /api/devices/remote/{deviceId}/snapshot`，body 含合法字段
- **THEN** 系统向该外域设备发送 SnapShotConfig SIP MESSAGE，返回 `{"sent": true}`

#### Scenario: 设备不存在时返回 404
- **WHEN** 调用抓拍接口但 deviceId 在数据库中不存在
- **THEN** 返回 `404 Not Found`，body 含 error 字段

#### Scenario: 南向接收 SnapShotConfig 命令路由
- **WHEN** 收到 CmdType=SnapShotConfig 的入站 SIP MESSAGE
- **THEN** DeviceCommandRouter 回复 200 OK，并将命令路由到目标本端设备或透传至外域设备

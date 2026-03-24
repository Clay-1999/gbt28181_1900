## ADDED Requirements

### Requirement: 录像查询接口
系统 SHALL 提供 REST 接口，接受设备类型、设备 ID 和时间范围，通过 SIP MESSAGE 向目标设备发送 RecordInfo 查询，等待响应并返回录像条目列表。

接口定义：
- `POST /api/devices/local/{gbDeviceId}/records/query`
- `POST /api/devices/remote/{deviceId}/records/query`

请求体：
```json
{
  "startTime": "2026-03-01T00:00:00",
  "endTime":   "2026-03-20T23:59:59",
  "type":      "all"
}
```

响应体（成功）：
```json
{
  "sumNum": 5,
  "items": [
    {
      "deviceId": "34020000001310000002",
      "name": "192.168.10.6",
      "startTime": "2026-03-10T08:00:00",
      "endTime":   "2026-03-10T09:00:00",
      "type": "time",
      "secrecy": 0,
      "filePath": "/record/xxx.mp4"
    }
  ]
}
```

响应体（超时）：HTTP 504

#### Scenario: 成功查询到录像列表
- **WHEN** 客户端 POST 有效时间范围到本端设备录像查询接口
- **THEN** 系统发送 SIP RecordInfo MESSAGE 到目标设备，等待响应后返回 200 和录像列表 JSON

#### Scenario: 设备不响应超时
- **WHEN** 客户端 POST 录像查询请求，10秒内设备无响应
- **THEN** 系统返回 HTTP 504

#### Scenario: 外域设备录像查询
- **WHEN** 客户端 POST 有效时间范围到外域设备录像查询接口
- **THEN** 系统通过该设备所属互联配置的 SIP 连接发送 RecordInfo 查询

### Requirement: SIP RecordInfo 请求报文
系统 SHALL 构建符合 GB/T 28181 A.2.4.5 的 RecordInfo 查询 XML：
```xml
<?xml version="1.0" encoding="GB2312"?>
<Query>
  <CmdType>RecordInfo</CmdType>
  <SN>{sn}</SN>
  <DeviceID>{deviceId}</DeviceID>
  <StartTime>{startTime}</StartTime>
  <EndTime>{endTime}</EndTime>
  <Type>{type}</Type>
</Query>
```
时间格式为 `yyyy-MM-dd'T'HH:mm:ss`。

#### Scenario: 构建标准 RecordInfo XML
- **WHEN** 系统需要发送录像查询时
- **THEN** 生成的 XML 包含 CmdType=RecordInfo、SN、DeviceID、StartTime、EndTime 字段

### Requirement: SIP RecordInfo 响应路由
系统 SHALL 识别入站 SIP MESSAGE 中 `<CmdType>RecordInfo</CmdType>` 的响应报文，通过 SN 匹配完成对应的 CompletableFuture。

#### Scenario: 响应路由到正确的 future
- **WHEN** 收到 CmdType=RecordInfo 且根节点为 Response 的 SIP MESSAGE
- **THEN** 从 XML 中提取 SN，找到对应 pending future 并 complete

#### Scenario: 无匹配的 SN
- **WHEN** 收到 RecordInfo 响应但 SN 无对应 pending future
- **THEN** 正常回复 200 OK 并忽略

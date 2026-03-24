## ADDED Requirements

### Requirement: 接收 Event:Alarm NOTIFY 并持久化
`AlarmNotifyHandler` SHALL 处理 `Event: Alarm` 的 SIP NOTIFY 请求，回复 200 OK，解析消息体 XML，将告警记录写入 `alarm_event` 表。

#### Scenario: 收到合法 Alarm NOTIFY
- **WHEN** 收到 SIP NOTIFY，Event 头为 `Alarm`，消息体为有效的 GB/T 28181 告警 XML
- **THEN** 回复 200 OK，解析 DeviceID、AlarmPriority、AlarmMethod、AlarmType、AlarmDescription、AlarmTime、Longitude、Latitude，写入 `alarm_event` 表，记录 INFO 日志

#### Scenario: 告警 XML 字段缺失或格式异常
- **WHEN** 消息体 XML 解析成功，但部分字段（如 Longitude、Latitude）缺失或格式异常
- **THEN** 回复 200 OK，缺失字段填 null，仍写入数据库，记录 DEBUG 日志

#### Scenario: 消息体为空或 XML 解析完全失败
- **WHEN** NOTIFY 消息体为空或无法解析为 XML
- **THEN** 回复 200 OK（避免对端重发），记录 WARN 日志，不写入数据库

---

### Requirement: AlarmEvent 数据模型
系统 SHALL 维护 `alarm_event` 表存储告警记录，字段包含：`id`（自增主键）、`deviceId`（告警设备 ID）、`alarmPriority`（优先级：1=一级/最高，2=二级，3=三级，4=四级/最低）、`alarmMethod`（报警方式，数字）、`alarmType`（报警类型，数字）、`alarmDescription`（告警描述）、`alarmTime`（告警发生时间，ISO 格式）、`longitude`（经度，可空）、`latitude`（纬度，可空）、`sourceIp`（来源 IP，从 Via 头提取）、`receivedAt`（平台接收时间）。

#### Scenario: 告警记录写入
- **WHEN** `AlarmNotifyHandler` 解析 NOTIFY 成功
- **THEN** 新建 `AlarmEvent` 实体，`receivedAt` 填当前时间，`sourceIp` 从请求 Via 头提取，保存到数据库

#### Scenario: 历史告警保留
- **WHEN** 同一设备多次上报告警
- **THEN** 每次均追加新记录，不覆盖历史，`id` 自增唯一

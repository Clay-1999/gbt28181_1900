## Source: catalog-query

## ADDED Requirements

### Requirement: 主动查询外域设备目录
系统 SHALL 在下联注册成功后，向对端发送 GB/T 28181 Catalog Query（SIP MESSAGE），请求对端推送设备目录。

#### Scenario: 下联注册成功触发查询
- **WHEN** 下联注册状态首次变为 ONLINE（收到对端 200 OK）
- **THEN** 向对端发送 SIP MESSAGE，Content-Type 为 `Application/MANSCDP+xml`，Body 包含 `<CmdType>Catalog</CmdType>` 和对端设备 ID

#### Scenario: 上联注册成功触发查询
- **WHEN** 对端上联注册成功（`upLinkStatus` 首次变为 ONLINE）
- **THEN** 向对端发送 SIP MESSAGE，Content-Type 为 `Application/MANSCDP+xml`，Body 包含 `<CmdType>Catalog</CmdType>` 和对端设备 ID

#### Scenario: 对端不支持或无响应
- **WHEN** 发送 Catalog Query 后对端无响应或返回错误
- **THEN** 静默忽略，不影响注册状态，记录 warn 日志

#### Scenario: SIP 栈未就绪时跳过
- **WHEN** 下联注册成功但 SIP Provider 尚未初始化
- **THEN** 跳过本次查询，记录 warn 日志

---

## Source: catalog-subscribe

## ADDED Requirements

### Requirement: 响应对端设备目录订阅
系统 SHALL 响应对端的 `SUBSCRIBE Catalog` 请求，从 `ivs1900_camera_mapping` 表读取本端设备列表，组装 GB/T 28181 XML，**构造并发送 SIP NOTIFY 请求**至订阅方（携带 `Event: Catalog`、`Subscription-State: active` 头）。

#### Scenario: 对端订阅设备目录
- **WHEN** 收到对端 `SUBSCRIBE` 请求（`Event: Catalog`）
- **THEN** 立即回复 `200 OK`，然后构造并实际发送 SIP NOTIFY 请求，XML Body 包含 `ivs1900_camera_mapping` 中所有记录，`Status` 字段 `ONLINE`→`"ON"`，`OFFLINE`→`"OFF"`

#### Scenario: 映射表为空时响应
- **WHEN** 收到 `SUBSCRIBE Catalog`，但 `ivs1900_camera_mapping` 表无数据
- **THEN** 回复 `200 OK`，发送 `SumNum=0` 的空 NOTIFY

#### Scenario: 未知 Event 类型
- **WHEN** 收到 `SUBSCRIBE` 请求，但 `Event` 头不是 `Catalog`
- **THEN** 回复 `489 Bad Event`

---

## Source: catalog-notify-receive

## ADDED Requirements

### Requirement: 接收并解析外域设备目录 NOTIFY
系统 SHALL 处理对端推送的 `NOTIFY`（Event: Catalog），解析 XML 中的设备列表并 upsert 到 `remote_device` 表。

#### Scenario: 收到有效 Catalog NOTIFY
- **WHEN** 收到对端 `NOTIFY` 请求，`Event: Catalog`，Body 包含合法的 `<DeviceList>`
- **THEN** 回复 `200 OK`，解析每个 `<Item>`，按 `deviceId` upsert 到 `remote_device` 表，`interconnectConfigId` 从 From header SIP ID 反查 `interconnect_config`

#### Scenario: NOTIFY 来源未知
- **WHEN** 收到 `NOTIFY`，但 From header 的 SIP ID 不在 `interconnect_config` 中
- **THEN** 回复 `200 OK`，丢弃设备数据，记录 warn 日志

#### Scenario: NOTIFY Body 为空或解析失败
- **WHEN** 收到 `NOTIFY`，但 Body 为空或 XML 格式非法
- **THEN** 回复 `200 OK`，不写入数据库，记录 error 日志

#### Scenario: 设备列表为空
- **WHEN** 收到 `NOTIFY`，`<DeviceList Num="0">` 或无 `<Item>`
- **THEN** 回复 `200 OK`，不写入数据库

---

## Source: northbound-catalog-notify

## ADDED Requirements

### Requirement: 响应上级 SUBSCRIBE 后发送 NOTIFY
系统 SHALL 在回复 `200 OK` 后，向订阅方实际发送 SIP NOTIFY 请求，Body 为包含本端所有设备的 GB/T 28181 Catalog XML。

#### Scenario: 发送 NOTIFY
- **WHEN** 收到上级平台 `SUBSCRIBE Catalog` 并回复 200 OK 后
- **THEN** 构造 SIP NOTIFY 请求（携带 `Event: Catalog`、`Subscription-State: active`、`Content-Type: Application/MANSCDP+xml`），Body 包含 `ivs1900_camera_mapping` 所有设备，发送至订阅方

#### Scenario: 设备列表为空时 NOTIFY
- **WHEN** `ivs1900_camera_mapping` 无数据
- **THEN** 发送 `SumNum=0`、`DeviceList Num="0"` 的空 NOTIFY，不报错

### Requirement: 注册成功后主动推送目录
系统 SHALL 在向上级平台注册成功（收到 REGISTER 200 OK）后，主动向该上级发送一次 Catalog NOTIFY，无需等待上级 SUBSCRIBE。

#### Scenario: 注册成功触发目录推送
- **WHEN** `SipRegistrationClient` 收到某互联配置的 REGISTER 200 OK（初始注册，非续约）
- **THEN** 异步向该互联配置对应的上级 SIP 地址发送 Catalog NOTIFY，包含当前全量本端设备列表

#### Scenario: 推送失败不影响注册流程
- **WHEN** 目录推送过程中发生异常（网络错误、SIP 发送失败等）
- **THEN** 记录 WARN 日志，注册状态不受影响，`downLinkStatus` 保持 ONLINE

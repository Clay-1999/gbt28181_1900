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

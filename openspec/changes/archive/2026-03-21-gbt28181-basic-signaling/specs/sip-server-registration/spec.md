## ADDED Requirements

### Requirement: REGISTER 200 OK 携带 Date 头域
`SipRegistrationServer` SHALL 在所有 REGISTER `200 OK` 响应（注册成功和注销成功）中加入 `Date` 头域，值为当前北京时间（Asia/Shanghai），格式 `YYYY-MM-DDTHH:MM:SS`，符合 GB/T 28181 A.2.1 校时规范。

#### Scenario: 注册成功响应携带 Date
- **WHEN** 下级设备 Digest 认证通过，系统返回 `200 OK`（Expires > 0）
- **THEN** 响应包含 `Date: YYYY-MM-DDTHH:MM:SS` 头域，时间为北京时间

#### Scenario: 注销响应携带 Date
- **WHEN** 下级设备发送 `Expires: 0` 注销，系统返回 `200 OK`
- **THEN** 响应包含 `Date` 头域

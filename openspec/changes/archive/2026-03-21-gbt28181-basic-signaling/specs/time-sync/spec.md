## ADDED Requirements

### Requirement: 校时 Date 头域下发
系统 SHALL 在向下级设备发送 REGISTER `200 OK` 响应时，携带 `Date` 头域，格式为 `YYYY-MM-DDTHH:MM:SS`（北京时间，GB/T 28181 A.2.1 规定格式），用于下级设备同步本地时钟。

#### Scenario: 注册成功时携带 Date 头域
- **WHEN** 下级设备 REGISTER 认证通过，系统准备返回 `200 OK`
- **THEN** 响应中包含 `Date` 头域，值为当前北京时间，格式 `YYYY-MM-DDTHH:MM:SS`

#### Scenario: 注销（Expires=0）时同样携带 Date 头域
- **WHEN** 下级设备发送 `Expires: 0` 注销请求，系统返回 `200 OK`
- **THEN** 响应中包含 `Date` 头域

#### Scenario: 认证失败时不携带 Date 头域
- **WHEN** 系统返回 `401 Unauthorized` 或 `403 Forbidden`
- **THEN** 响应中不包含 `Date` 头域

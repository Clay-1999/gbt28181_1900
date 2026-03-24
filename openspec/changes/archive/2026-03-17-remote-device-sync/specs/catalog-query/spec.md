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

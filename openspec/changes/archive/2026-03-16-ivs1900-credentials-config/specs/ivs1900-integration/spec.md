## MODIFIED Requirements

### Requirement: IVS1900 登录与 Session 保活
系统 SHALL 在启动时登录 IVS1900，获取 JSESSIONID，并定期保活。登录所需的 `userName` 和 `password` SHALL 从配置文件 `ivs1900.username` / `ivs1900.password` 读取，支持通过环境变量 `IVS1900_USERNAME` / `IVS1900_PASSWORD` 覆盖。

#### Scenario: 启动时登录
- **WHEN** 应用启动或检测到 Session 失效
- **THEN** 从 `Ivs1900Properties` 读取 `username` 和 `password`，调用 `POST /loginInfo/login/v1.0`，从响应 Cookie 中提取 JSESSIONID，存储到内存

#### Scenario: 定期保活
- **WHEN** 距上次保活超过 5 分钟
- **THEN** 调用 `GET /common/keepAlive`，携带 JSESSIONID，保持 Session 有效

#### Scenario: Session 失效重连
- **WHEN** 任意 IVS1900 接口返回 401 Unauthorized
- **THEN** 立即重新登录，获取新 JSESSIONID，重试失败的请求

#### Scenario: 凭据未配置时启动失败
- **WHEN** `ivs1900.username` 或 `ivs1900.password` 为空
- **THEN** 应用启动时抛出配置校验异常，日志明确提示缺少凭据配置

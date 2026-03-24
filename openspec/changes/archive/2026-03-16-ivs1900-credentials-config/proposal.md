## Why

当前 `Ivs1900Properties` 只有 `baseUrl` 字段，缺少 `username` 和 `password`，导致 IVS1900 登录接口无法从配置文件读取凭据，Session 管理功能无法正常工作。

## What Changes

- `Ivs1900Properties` 新增 `username`、`password` 字段
- `application.yml` 新增 `ivs1900.username` 和 `ivs1900.password` 配置项（支持环境变量注入）
- IVS1900 登录服务在调用 `POST /loginInfo/login/v1.0` 时使用配置中的凭据

## Capabilities

### New Capabilities

（无新能力，仅补全现有能力的配置支持）

### Modified Capabilities

- `ivs1900-integration`: 补充 Session 管理中登录凭据的配置来源，要求系统从 `ivs1900.username` / `ivs1900.password` 读取凭据并用于登录

## Impact

- `src/main/java/com/example/gbt28181/config/Ivs1900Properties.java`
- `src/main/resources/application.yml`
- IVS1900 Session 管理相关服务类（登录逻辑）

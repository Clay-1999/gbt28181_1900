## Context

当前 `Ivs1900Properties` 只绑定了 `ivs1900.base-url`，`username` 和 `password` 字段缺失。IVS1900 登录接口 `POST /loginInfo/login/v1.0` 需要这两个字段，否则 Session 管理无法工作。

## Goals / Non-Goals

**Goals:**
- 在 `Ivs1900Properties` 中新增 `username`、`password` 字段
- 在 `application.yml` 中新增对应配置项，支持环境变量注入
- 登录服务从 `Ivs1900Properties` 读取凭据

**Non-Goals:**
- 不实现凭据加密存储
- 不修改登录接口本身的实现逻辑（仅补全凭据来源）

## Decisions

**决策：使用环境变量注入敏感凭据**

`application.yml` 中配置：
```yaml
ivs1900:
  base-url: ${IVS1900_BASE_URL:http://localhost:8081}
  username: ${IVS1900_USERNAME:admin}
  password: ${IVS1900_PASSWORD:Admin@123}
```

理由：与现有 `base-url` 的处理方式保持一致，开发环境有默认值，生产环境通过环境变量覆盖，避免明文密码提交到代码仓库。

**决策：直接在 `Ivs1900Properties` 添加字段，不新建独立配置类**

理由：凭据与连接地址属于同一配置域，放在同一个 `@ConfigurationProperties` 类中更内聚。

## Risks / Trade-offs

- [风险] 默认密码 `Admin@123` 若未在生产环境覆盖会造成安全隐患 → 在配置注释中明确标注"生产环境必须通过环境变量覆盖"
- [风险] `password` 字段在日志中可能被打印 → 登录服务中禁止打印 password 字段

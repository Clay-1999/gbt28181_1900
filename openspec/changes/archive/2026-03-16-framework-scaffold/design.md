## Context

项目从零开始，Java 21（Azul JDK）、无现有源码、无构建系统。目标是建立 GB/T 28181 互联系统的完整框架骨架，为后续 SIP 信令、设备目录、ivs1900 命令网关等功能模块提供稳固基础。详细架构见项目根目录 `ARCHITECTURE.md`。

## Goals / Non-Goals

**Goals:**
- Maven 项目结构及所有依赖声明
- 四层包结构：`domain`（实体/Repository）、`config`（Spring 配置）、`sip`（SIP 信令骨架）、`api`（REST 控制器）
- 三个 JPA 实体 + Repository + AES 加密 Converter
- SipStackManager 骨架（含状态机，不实现 SIP 消息收发）
- REST API 骨架（端点定义 + DTO + 校验，不实现业务逻辑）
- Vue 3 前端项目初始化（路由 + 状态管理 + UI 库 + 开发代理）

**Non-Goals:**
- SIP 实际报文处理（REGISTER、SUBSCRIBE、NOTIFY、MESSAGE）
- ivs1900 REST API 调用实现
- 前端页面业务逻辑
- 测试代码

## Decisions

### 1. 包结构：四层扁平分层

```
com.example.gbt28181
├── domain/
│   ├── entity/          LocalSipConfig, InterconnectConfig, Ivs1900CameraMapping
│   ├── repository/      对应 JPA Repository
│   └── converter/       EncryptedStringConverter（AES）
├── config/
│   ├── SipProperties    @ConfigurationProperties 绑定 SIP 配置
│   ├── AppProperties    @ConfigurationProperties 绑定 app.* 配置
│   └── WebConfig        CORS、Jackson 配置
├── sip/
│   └── SipStackManager  JAIN-SIP 生命周期管理
└── api/
    ├── controller/      LocalSipConfigController, InterconnectConfigController
    ├── dto/             Request/Response DTO
    └── exception/       GlobalExceptionHandler, 自定义异常
```

**理由**：职责清晰，后续 SIP 模块（server/client/gateway）均在 `sip/` 下扩展，不影响 `api/` 层。

### 2. AES 加密：JPA AttributeConverter 透明处理

**决定**：实现 `EncryptedStringConverter implements AttributeConverter<String, String>`，在 `LocalSipConfig.password` 和 `InterconnectConfig.password` 字段上标注 `@Convert`，读写数据库时自动加解密。

**理由**：业务代码无需感知加密，密钥从 `app.secret-key`（`application.yml`）注入，生产环境通过环境变量覆盖。

### 3. SipStackManager 状态机

```
           ┌──────────┐
  启动成功  │  RUNNING │◀────────────────────────────┐
           └────┬─────┘                              │
         用户修改│配置                          热重载成功
                ▼                                    │
           ┌──────────┐      端口检查/重建失败   ┌───┴──────┐
           │RELOADING │─────────────────────────▶│  ERROR   │
           └──────────┘                          └──────────┘
                                                      │
                                               用户修正后重试
```

骨架阶段：`start()` 仅更新状态为 RUNNING，`stop()` 更新为 RELOADING，不做实际 JAIN-SIP 操作——留给后续 SIP 模块填充。

### 4. application.yml 分层配置

```yaml
app:
  secret-key: ${APP_SECRET_KEY:changeme-32-chars}  # AES 密钥，生产用环境变量

sip:
  # 仅声明，首次启动从 local_sip_config 表读取，此处作为开发默认值
  default-port: 5060
  default-transport: UDP

ivs1900:
  base-url: http://localhost:8081  # ivs1900 REST API 地址

spring:
  datasource:
    url: jdbc:h2:file:./data/gbt28181
  jpa:
    hibernate:
      ddl-auto: update
```

### 5. Vue 3 项目放在 frontend/ 独立目录

Vite 开发代理将 `/api` 转发到 `http://localhost:8080`，生产构建产物由 `frontend-maven-plugin` 复制到 `src/main/resources/static/`（后续 Maven 配置阶段完成）。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| JAIN-SIP 依赖在 Maven Central 坐标不稳定 | 使用 `gov.nist.javax.sip:jain-sip-ri:1.3.0-91` 锁定版本 |
| H2 文件数据库路径问题（相对路径） | `jdbc:h2:file:./data/gbt28181`，启动目录即工作目录 |
| AES 密钥硬编码默认值安全风险 | 明确注释要求生产环境通过 `APP_SECRET_KEY` 环境变量注入 |

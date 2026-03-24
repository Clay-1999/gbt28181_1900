## Why

项目目前无任何源码。在实现 SIP 信令、设备目录同步、ivs1900 命令网关等核心功能之前，需要先搭建完整的框架骨架，确立包结构、依赖体系、数据模型和 API 边界，使后续功能开发可以直接在稳固的基础上填充逻辑。

## What Changes

- 新增 Maven 项目结构（`pom.xml`），引入 Spring Boot 3.x、JAIN-SIP（jain-sip-ri）、Spring Data JPA、H2、Lombok 依赖
- 新增 Spring Boot 主类及四层包结构：`domain`、`config`、`sip`、`api`
- 新增 JPA 实体：`LocalSipConfig`（本端 SIP 参数）、`InterconnectConfig`（互联关系）、`Ivs1900CameraMapping`（本端相机国标 ID 映射）
- 新增 AES 加密工具类（`CryptoUtils`）及 JPA `AttributeConverter`，密码字段透明加解密
- 新增 `SipStackManager` 骨架：JAIN-SIP 初始化、热重载、状态跟踪（RUNNING/RELOADING/ERROR）
- 新增 REST API 控制器骨架：`LocalSipConfigController`、`InterconnectConfigController`，含请求/响应 DTO 及 Bean Validation
- 新增统一异常处理（`GlobalExceptionHandler`）
- 新增 `application.yml` 基础配置（H2 数据源、JPA、AES 密钥、ivs1900 base-url、SIP 默认参数）
- 新增 Vue 3 前端项目（`frontend/` 目录），安装 Element Plus、Vue Router、Pinia、Axios，配置 Vite 开发代理

## Capabilities

### New Capabilities

- `project-structure`: Maven + Spring Boot 项目骨架，包结构，依赖管理
- `data-model`: JPA 实体定义、Repository、AES 加密 Converter
- `sip-stack-skeleton`: SipStackManager 骨架，JAIN-SIP 初始化与热重载流程框架
- `rest-api-skeleton`: REST 控制器骨架、DTO、统一异常处理
- `frontend-scaffold`: Vue 3 前端项目初始化，路由、状态管理、UI 组件库、开发代理

### Modified Capabilities

（无已有规格）

## Impact

- **新增依赖**：Spring Boot 3.x、jain-sip-ri 1.3.x、Spring Data JPA、H2、Lombok、Vue 3、Element Plus、Vite、Axios、Pinia
- **新增文件**：`pom.xml`、`src/`、`frontend/`、`application.yml`
- **后续模块直接基于此骨架填充逻辑**，不需要再做项目初始化工作

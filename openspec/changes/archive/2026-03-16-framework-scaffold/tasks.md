## 1. Maven 项目骨架

- [x] 1.1 创建 `pom.xml`：parent 为 `spring-boot-starter-parent 3.2.x`，引入 `spring-boot-starter-web`、`spring-boot-starter-data-jpa`、`h2`、`lombok`、`jain-sip-ri:1.3.0-91`（需添加 NIST 仓库）、`spring-boot-starter-validation`
- [x] 1.2 创建主类 `src/main/java/com/example/gbt28181/Gbt28181Application.java`，`@SpringBootApplication`
- [x] 1.3 创建包目录：`domain/entity`、`domain/repository`、`domain/converter`、`config`、`sip`、`api/controller`、`api/dto`、`api/exception`
- [x] 1.4 创建 `src/main/resources/application.yml`：H2 文件数据源（`jdbc:h2:file:./data/gbt28181`）、JPA `ddl-auto: update`、`app.secret-key`、`ivs1900.base-url`、H2 控制台开启
- [x] 1.5 验证 `mvn compile` 成功

## 2. 数据模型

- [x] 2.1 实现 `EncryptedStringConverter`
- [x] 2.2 实现 `LocalSipConfig` 实体
- [x] 2.3 实现 `InterconnectConfig` 实体
- [x] 2.4 实现 `Ivs1900CameraMapping` 实体
- [x] 2.5 创建 `LocalSipConfigRepository`、`InterconnectConfigRepository`、`Ivs1900CameraMappingRepository`
- [x] 2.6 创建 `SipStackStatus` 枚举：`RUNNING`、`RELOADING`、`ERROR`
- [x] 2.7 验证应用启动后三张表在 H2 控制台可见

## 3. 配置类

- [x] 3.1 实现 `AppProperties`
- [x] 3.2 实现 `SipProperties`
- [x] 3.3 实现 `Ivs1900Properties`
- [x] 3.4 实现 `WebConfig`：CORS + Jackson 时间格式

## 4. SIP Stack 骨架

- [x] 4.1 实现 `SipStackManager` Spring Bean
- [x] 4.2 实现 `reload(LocalSipConfig config)` 方法
- [x] 4.3 实现 `LocalSipConfigInitializer`

## 5. REST API 骨架

- [x] 5.1 实现 `LocalSipConfigRequest` DTO
- [x] 5.2 实现 `LocalSipConfigResponse` DTO
- [x] 5.3 实现 `LocalSipConfigController`
- [x] 5.4 实现 `InterconnectConfigRequest` DTO
- [x] 5.5 实现 `InterconnectConfigResponse` DTO
- [x] 5.6 实现 `InterconnectConfigController`
- [x] 5.7 实现 `ResourceNotFoundException`
- [x] 5.8 实现 `GlobalExceptionHandler`

## 6. 前端脚手架

- [x] 6.1 在 `frontend/` 目录创建 Vue 3 项目结构
- [x] 6.2 安装依赖：`npm install element-plus axios`
- [x] 6.3 在 `main.js` 中全局注册 Element Plus
- [x] 6.4 配置 `vite.config.js` 开发代理：`/api` → `http://localhost:8080`
- [x] 6.5 在 `router/index.js` 中添加路由：`/local-config`、`/interconnects`
- [x] 6.6 创建占位页面组件：`LocalConfigView.vue`、`InterconnectsView.vue`
- [x] 6.7 验证 `npm run build` 成功，前端可构建

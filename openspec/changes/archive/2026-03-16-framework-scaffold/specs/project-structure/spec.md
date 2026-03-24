## ADDED Requirements

### Requirement: Maven 项目结构

系统 SHALL 提供合法的 Maven 项目结构，包含 `pom.xml`、`src/main/java`、`src/main/resources`，可通过 `mvn compile` 编译成功。

#### Scenario: 项目可编译

- **WHEN** 在项目根目录执行 `mvn compile`
- **THEN** 编译成功，无错误，target 目录生成 class 文件

#### Scenario: 依赖声明完整

- **WHEN** 执行 `mvn dependency:resolve`
- **THEN** 所有依赖（Spring Boot 3.x、jain-sip-ri、H2、Lombok）均可解析，无缺失

---

### Requirement: Spring Boot 应用可启动

系统 SHALL 提供 Spring Boot 主类，应用可正常启动并监听 HTTP 端口。

#### Scenario: 应用正常启动

- **WHEN** 执行 `mvn spring-boot:run`
- **THEN** 应用在 8080 端口启动成功，日志输出 "Started Gbt28181Application"

#### Scenario: 包结构符合规范

- **WHEN** 查看源码目录
- **THEN** 存在 `domain/`、`config/`、`sip/`、`api/` 四个子包

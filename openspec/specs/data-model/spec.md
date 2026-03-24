## Source: data-model

## ADDED Requirements

### Requirement: JPA 实体定义

系统 SHALL 定义三个 JPA 实体并在应用启动时自动建表：`LocalSipConfig`（单行）、`InterconnectConfig`（多行）、`Ivs1900CameraMapping`（多行）。

#### Scenario: 启动时自动建表

- **WHEN** 应用首次启动
- **THEN** H2 数据库中自动创建 `local_sip_config`、`interconnect_config`、`ivs1900_camera_mapping` 三张表

#### Scenario: Repository 可用

- **WHEN** 通过 Spring 注入对应 Repository
- **THEN** 可调用 `save()`、`findById()`、`findAll()` 等基础方法无异常

---

### Requirement: 密码字段 AES 加密存储

系统 SHALL 对 `LocalSipConfig.password` 和 `InterconnectConfig.password` 字段自动进行 AES 加密存储、解密读取，业务代码无需感知。

#### Scenario: 密码写入数据库时加密

- **WHEN** 保存含密码的实体到数据库
- **THEN** 数据库中存储的密码为密文（非明文）

#### Scenario: 密码从数据库读取时解密

- **WHEN** 从数据库读取含密码的实体
- **THEN** 实体对象的 password 字段为明文，可直接使用

---

## Source: local-sip-config

## Requirements

### Requirement: 本端 SIP 配置持久化
系统 SHALL 以单例模式持久化本端 SIP 参数（固定主键 id=1），包含：`deviceId`（20 位国标编码，NOT NULL）、`domain`（SIP 域）、`sipIp`（监听 IP）、`sipPort`（监听端口，默认 5060）、`transport`（UDP/TCP，默认 UDP）、`password`（AES 加密存储）、`expires`（注册有效期，默认 3600s）、`status`（`RUNNING` / `RELOADING` / `ERROR`）、`errorMsg`（热重载失败原因，可空）。

#### Scenario: 查询本端配置
- **WHEN** 调用 `GET /api/local-config`
- **THEN** 返回当前配置，密码字段以 `"***"` 掩码返回

#### Scenario: 首次启动无配置
- **WHEN** 数据库中不存在本端 SIP 配置（首次启动）
- **THEN** 系统自动创建默认记录（`status=ERROR`，`errorMsg="未配置"`），前端提示用户通过 UI 完善参数

#### Scenario: 首次启动有 deviceId
- **WHEN** 数据库存在本端配置且 `deviceId` 已设置
- **THEN** 应用启动完成后自动调用 `SipStackManager.reload()` 启动 SIP 栈

---

### Requirement: 更新本端配置并触发热重载
调用 `PUT /api/local-config` 保存新参数时，系统 SHALL 触发 SIP Stack 热重载。若 SIP 栈正在重载中（`RELOADING`），系统 SHALL 返回 `409 Conflict` 拒绝此次请求。

#### Scenario: 配置更新触发热重载
- **WHEN** 调用 `PUT /api/local-config` 且 SIP 栈当前不在 `RELOADING` 状态
- **THEN** 保存新配置，异步触发 `SipStackManager.reload()`，立即返回 `202 Accepted`

#### Scenario: 重载进行中拒绝更新
- **WHEN** 调用 `PUT /api/local-config` 且 SIP 栈正在 `RELOADING`
- **THEN** 返回 `409 Conflict`，不保存新配置

---

### Requirement: 输入校验
`PUT /api/local-config` 的请求体 SHALL 满足以下校验规则：
- `deviceId`：必填，长度恰好 20 位
- `domain`：必填，非空
- `sipIp`：必填，非空
- `sipPort`：必填，范围 1–65535
- `transport`：必须为 `"UDP"` 或 `"TCP"`
- `password`：必填，非空
- `expires`：必填，最小值 60

#### Scenario: 校验失败返回 400
- **WHEN** 请求体字段不满足上述规则
- **THEN** 返回 `400 Bad Request`，包含具体校验错误信息

---

### Requirement: 实时状态查询
系统 SHALL 提供独立端点供前端轮询 SIP 栈运行状态。

#### Scenario: 查询 SIP 栈状态
- **WHEN** 调用 `GET /api/local-config/status`
- **THEN** 返回 `{ status: "RUNNING"|"RELOADING"|"ERROR", errorMsg: string|null }`

---

## Source: local-sip-config-service

## ADDED Requirements

### Requirement: 读取本端 SIP 配置

系统 SHALL 从 `local_sip_config` 表读取当前本端 SIP 参数，并将密码脱敏后返回给调用方。

#### Scenario: 正常读取

- **WHEN** 调用 `GET /api/local-config`
- **THEN** 返回 200，body 含 `deviceId`、`domain`、`sipIp`、`sipPort`、`transport`、`username`、`password="***"`、`expires`、`status`、`errorMsg`

#### Scenario: 首次启动无配置

- **WHEN** 数据库中 `local_sip_config` 仅有 `LocalSipConfigInitializer` 写入的默认行（`deviceId` 为 null）
- **THEN** 返回 200，`status=ERROR`，`errorMsg="本端 SIP 参数未配置"`

---

### Requirement: 更新本端 SIP 配置并触发热重载

系统 SHALL 将新本端参数写入 `local_sip_config` 表，并异步触发 `SipStackManager.reload()`，立即返回 202。

#### Scenario: 提交合法配置

- **WHEN** 请求 `PUT /api/local-config` 携带合法参数
- **THEN** 参数写入数据库，异步触发热重载，立即返回 202 Accepted，`status` 字段为 `RELOADING`

#### Scenario: 热重载进行中再次提交

- **WHEN** `SipStackManager.status == RELOADING` 时再次请求 `PUT /api/local-config`
- **THEN** 返回 409 Conflict，body 含提示"SIP Stack 正在重载，请稍后再试"

#### Scenario: 参数校验失败

- **WHEN** 请求 `PUT /api/local-config` 缺少必填字段或格式非法
- **THEN** 返回 400，body 含字段级错误，不触发热重载

---

## Source: rest-api-skeleton

## ADDED Requirements

### Requirement: 本端 SIP 配置 REST API 骨架

系统 SHALL 提供本端 SIP 配置的 REST 端点定义，请求/响应 DTO 及校验规则完整，业务逻辑返回占位响应。

#### Scenario: GET /api/local-config 可访问

- **WHEN** 请求 `GET /api/local-config`
- **THEN** 返回 200，body 为 `LocalSipConfigResponse` JSON 结构（骨架阶段返回默认值或空对象）

#### Scenario: PUT /api/local-config 参数校验生效

- **WHEN** 请求 `PUT /api/local-config` 携带非法参数（如缺少 deviceId）
- **THEN** 返回 400，body 含字段级错误信息

#### Scenario: GET /api/local-config/status 可访问

- **WHEN** 请求 `GET /api/local-config/status`
- **THEN** 返回 200，body 含 `status` 字段

---

### Requirement: 互联配置 REST API 骨架

系统 SHALL 提供互联配置 CRUD 端点定义，请求/响应 DTO 及校验规则完整。

#### Scenario: GET /api/interconnects 可访问

- **WHEN** 请求 `GET /api/interconnects`
- **THEN** 返回 200，body 为数组 JSON（骨架阶段返回空数组）

#### Scenario: POST /api/interconnects 参数校验生效

- **WHEN** 请求 `POST /api/interconnects` 携带非法参数（如端口超出范围）
- **THEN** 返回 400，body 含字段级错误信息

---

### Requirement: 统一异常处理

系统 SHALL 对所有 REST 端点的异常统一处理，返回标准 JSON 错误格式。

#### Scenario: 参数校验失败统一格式

- **WHEN** 任意端点收到校验失败的请求
- **THEN** 返回 400，body 格式为 `{"errors": [{"field": "...", "message": "..."}]}`

#### Scenario: 资源不存在统一格式

- **WHEN** 任意端点抛出 `ResourceNotFoundException`
- **THEN** 返回 404，body 格式为 `{"error": "..."}`

---

## Source: project-structure

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

---

## Source: device-list-api

## ADDED Requirements

### Requirement: 本端设备视频流 REST 端点
系统 SHALL 在 `DeviceController` 中提供本端设备视频流启动和停止端点。

#### Scenario: stream/start 端点存在
- **WHEN** 客户端发送 `POST /api/devices/local/{gbDeviceId}/stream/start`
- **THEN** 系统处理请求并返回 `{"streamUrl": "<url>"}` 或错误响应

#### Scenario: stream/stop 端点存在
- **WHEN** 客户端发送 `POST /api/devices/local/{gbDeviceId}/stream/stop`
- **THEN** 系统处理请求并返回 HTTP 200

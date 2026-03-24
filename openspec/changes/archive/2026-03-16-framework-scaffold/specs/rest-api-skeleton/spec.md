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

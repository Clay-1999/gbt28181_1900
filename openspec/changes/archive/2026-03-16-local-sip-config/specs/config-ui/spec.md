## ADDED Requirements

### Requirement: 本端配置页面

系统前端 SHALL 提供本端 SIP 参数配置页面，支持查看、编辑、保存，并实时展示 SIP Stack 运行状态。

#### Scenario: 页面加载显示当前配置

- **WHEN** 用户访问 `/local-config`
- **THEN** 页面加载后调用 `GET /api/local-config`，将返回值填充到 Element Plus 表单各字段（密码显示 `***`），并显示当前 SIP Stack 状态 Badge（RUNNING=绿/RELOADING=黄/ERROR=红）

#### Scenario: 保存配置触发热重载

- **WHEN** 用户填写合法参数后点击「保存」
- **THEN** 调用 `PUT /api/local-config`，收到 202 后：禁用保存按钮，状态 Badge 变为 RELOADING，开始每 5 秒轮询 `GET /api/local-config/status`，直到 status 变为 RUNNING 或 ERROR（最多 60 秒），恢复按钮可用

#### Scenario: 热重载失败展示错误

- **WHEN** 轮询到 `status=ERROR`
- **THEN** 状态 Badge 显示红色 ERROR，`errorMsg` 内容以 `el-alert` 展示在表单下方

#### Scenario: 重载中重复提交被拒绝

- **WHEN** 热重载进行中用户再次点击「保存」（服务端返回 409）
- **THEN** 前端展示提示"SIP Stack 正在重载，请稍后再试"，不发起第二次请求

---

### Requirement: 互联管理页面

系统前端 SHALL 提供互联配置管理页面，支持列表查看、新增、编辑、删除。

#### Scenario: 列表展示

- **WHEN** 用户访问 `/interconnects`
- **THEN** 调用 `GET /api/interconnects`，以 Element Plus Table 展示：名称、对端 SIP ID、对端地址:端口、启用状态、创建时间，密码列不展示

#### Scenario: 新增互联配置

- **WHEN** 用户点击「新增」，填写合法表单后提交
- **THEN** 调用 `POST /api/interconnects`，成功后关闭弹窗，刷新列表，Element Plus Message 提示"新增成功"

#### Scenario: 编辑互联配置

- **WHEN** 用户点击某行「编辑」，修改后提交
- **THEN** 调用 `PUT /api/interconnects/{id}`，成功后关闭弹窗，刷新列表

#### Scenario: 删除互联配置

- **WHEN** 用户点击「删除」并在 MessageBox 中确认
- **THEN** 调用 `DELETE /api/interconnects/{id}`，成功后列表移除该行，提示"删除成功"

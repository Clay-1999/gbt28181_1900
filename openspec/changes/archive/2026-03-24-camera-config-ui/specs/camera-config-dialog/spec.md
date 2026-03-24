## ADDED Requirements

### Requirement: 设备列表配置入口
设备列表页（本端和外域）SHALL 在每行提供"配置"操作按钮，点击后弹出该设备的配置对话框。

#### Scenario: 点击配置按钮
- **WHEN** 用户点击设备行的"配置"按钮
- **THEN** 弹出配置对话框，自动加载当前配置，显示加载状态

### Requirement: 配置对话框分 tab 展示
配置对话框 SHALL 使用 tab 页分别展示 VideoParamAttribute、OSDConfig、PictureMask、FrameMirror 四种配置类型，切换 tab 时自动加载对应配置。

#### Scenario: 切换配置 tab
- **WHEN** 用户切换到 OSDConfig tab
- **THEN** 系统调用 GET 接口加载 OSD 配置，填充表单字段

#### Scenario: 配置加载失败
- **WHEN** GET 接口返回错误或超时
- **THEN** 显示错误提示，表单字段置空

### Requirement: 配置表单保存
配置对话框 SHALL 提供"保存"按钮，将表单当前值通过 PUT 接口下发，并显示操作结果。

#### Scenario: 保存成功
- **WHEN** 用户修改字段后点击"保存"，PUT 接口返回 `{"success": true}`
- **THEN** 显示成功提示，对话框保持打开

#### Scenario: 保存失败
- **WHEN** PUT 接口返回 `{"success": false}` 或网络错误
- **THEN** 显示失败提示，表单数据保留

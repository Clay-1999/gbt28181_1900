## ADDED Requirements

### Requirement: 录像查询顶级页签
前端主页签栏 SHALL 新增"录像查询"页签，与"本端设备"/"外域设备"页签同级。页签内包含查询条件区域和录像列表区域。

#### Scenario: 录像查询页签可见
- **WHEN** 用户打开设备列表页面
- **THEN** 顶部页签栏显示"本端设备"、"外域设备"、"录像查询"三个页签

#### Scenario: 切换到录像查询页签
- **WHEN** 用户点击"录像查询"页签
- **THEN** 显示查询条件区域（相机选择、时间范围、查询按钮）和空的录像列表区域

### Requirement: 录像查询条件
录像查询页签 SHALL 提供以下查询条件：
- 相机选择下拉框：合并展示本端设备和外域设备，格式为"设备名称（本端/互联平台名称）"，选项按来源分组
- 开始时间选择器（`el-date-picker type="datetime"`），默认为当前时间前 24 小时
- 结束时间选择器，默认为当前时间
- "查询"按钮

#### Scenario: 相机下拉合并本端和外域设备
- **WHEN** 用户打开相机下拉框
- **THEN** 显示两组选项：本端设备（来自 GET /api/devices/local）和外域设备（来自 GET /api/devices/remote），分组标题分别为"本端设备"和外域互联平台名称

#### Scenario: 提交查询
- **WHEN** 用户选择相机和时间范围后点击"查询"
- **THEN** 根据设备类型调用 `POST /api/devices/{type}/{deviceId}/records/query`，展示加载状态

### Requirement: 录像列表展示
录像查询结果 SHALL 以表格形式展示，列包含：开始时间、结束时间、录像类型、文件路径。

#### Scenario: 有录像结果
- **WHEN** 查询成功返回录像列表
- **THEN** 以 `el-table` 展示录像条目，显示 sumNum 总数

#### Scenario: 无录像结果
- **WHEN** 查询成功但 items 为空
- **THEN** 显示 `el-empty` 提示"暂无录像"

#### Scenario: 查询超时或设备不支持
- **WHEN** 查询接口返回 504
- **THEN** 显示 `el-alert` 警告"设备不支持录像查询或查询超时"

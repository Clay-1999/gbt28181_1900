## ADDED Requirements

### Requirement: 独立录像管理页面
系统 SHALL 提供独立路由页面 `/recordings`（`RecordingView.vue`），顶部导航中显示"录像管理"入口，用户可直接访问。

#### Scenario: 从导航进入录像管理
- **WHEN** 用户点击顶部导航"录像管理"
- **THEN** 路由跳转至 `/recordings`，显示录像管理页面

#### Scenario: 直接访问 URL
- **WHEN** 用户在浏览器地址栏输入 `/recordings`
- **THEN** 显示录像管理页面，不跳转到其他页面

### Requirement: 设备选择与时间范围查询
系统 SHALL 在录像管理页面提供设备选择下拉框（包含本端和外域设备分组）、开始时间和结束时间选择器，以及"查询"按钮。点击查询后，调用 `POST /api/devices/{type}/{id}/records/query` 获取录像列表。

#### Scenario: 查询到录像列表
- **WHEN** 用户选择设备、设置时间范围后点击"查询"
- **THEN** 接口返回 200，页面展示录像条目表格（录像名称、开始时间、结束时间、类型、操作）

#### Scenario: 设备无录像返回空列表
- **WHEN** 查询结果 sumNum=0
- **THEN** 表格显示"暂无录像"空状态

#### Scenario: 设备响应超时
- **WHEN** 接口返回 504
- **THEN** 页面显示超时警告提示，不显示表格

#### Scenario: 未选设备直接查询
- **WHEN** 用户未选择设备点击"查询"
- **THEN** 提示"请先选择相机"，不发起请求

### Requirement: 录像播放
系统 SHALL 在录像列表每行提供"播放"按钮，点击后调用 `POST /api/devices/{type}/{id}/playback/start`（携带 startTime/endTime），获取 streamUrl 后在页面内嵌播放器播放 HTTP-FLV 流。同时提供"停止"按钮，调用 `POST /api/devices/{type}/{id}/playback/stop`。

#### Scenario: 点击播放录像
- **WHEN** 用户点击录像条目的"播放"按钮
- **THEN** 调用 playback/start，成功后播放器显示并开始播放视频流

#### Scenario: 停止回放
- **WHEN** 用户点击"停止"按钮
- **THEN** 调用 playback/stop，播放器关闭

#### Scenario: 回放启动超时
- **WHEN** playback/start 接口返回 504
- **THEN** 提示"回放启动超时"，播放器不显示

### Requirement: 设备列表页移除录像 tab
系统 SHALL 从 `DevicesView.vue` 的"设备列表"页面移除"录像查询" tab，以及相关的 data 状态和 methods（`queryRecords`、`startPlayback`、`stopPlayback` 等）。

#### Scenario: 设备列表页不再显示录像查询 tab
- **WHEN** 用户访问 `/devices` 设备列表页
- **THEN** 页面只显示"本端设备"和"外域设备"两个 tab，无"录像查询" tab

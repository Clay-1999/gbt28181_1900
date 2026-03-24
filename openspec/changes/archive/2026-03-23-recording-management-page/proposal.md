## Why

当前录像查询功能嵌套在"设备列表"页面的一个 tab 中，与实时流操作混在一起，不易发现、布局局促。用户需要一个专属的"录像管理"页面，集中提供录像查询和历史回放功能。

## What Changes

- 新建独立的 `RecordingView.vue` 页面，路由路径 `/recordings`
- 从 `DevicesView.vue` 中移除"录像查询" tab，改由新页面承载
- 新页面支持：选择设备、设置时间范围、查询录像列表、点击播放录像（SIP INVITE Playback）
- 顶部导航栏增加"录像管理"入口
- 回放播放器复用现有 HTTP-FLV 播放逻辑（`flv.js`），与 DevicesView 实时流一致

## Capabilities

### New Capabilities
- `recording-management-ui`: 独立的录像管理页面——设备选择、时间范围查询录像列表、点击播放历史录像（INVITE Playback），并支持停止回放

### Modified Capabilities
- `device-list-api`: DevicesView 移除录像查询 tab，不涉及 spec 级别行为变更，无需修改（仅 UI 重构）

## Impact

- 新增 `frontend/src/views/RecordingView.vue`
- 修改 `frontend/src/router/index.js`（新增 `/recordings` 路由）
- 修改 `frontend/src/App.vue`（导航增加录像管理入口）
- 修改 `frontend/src/views/DevicesView.vue`（移除录像查询 tab 相关代码）
- 后端 REST 接口无需改动（`/api/devices/{type}/{id}/records/query`、`/api/devices/{type}/{id}/playback/*` 已存在）

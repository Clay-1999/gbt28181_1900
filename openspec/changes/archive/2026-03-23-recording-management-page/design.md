## Context

前端 `DevicesView.vue` 当前有三个 tab：本端设备、外域设备、录像查询。录像查询在设备列表页嵌套较深，用户需要先进入设备列表才能查录像，且设备列表的刷新逻辑与录像查询耦合（共用同一个 `refresh()` 函数）。路由层面也没有独立入口，无法通过 URL 直接到达录像功能。

已有能力：
- 后端 `RecordQueryService` + `POST /api/devices/{type}/{id}/records/query` 完整实现
- 后端 `SipPlaybackService` + `POST /api/devices/{type}/{id}/playback/{start|stop|control}` 完整实现
- 前端 `DevicesView.vue` 已包含录像查询 UI 和回放 UI（含 flv.js 播放器）
- `App.vue` 已有侧边导航

## Goals / Non-Goals

**Goals:**
- 将录像查询和回放 UI 迁移至独立页面 `RecordingView.vue`，路由 `/recordings`
- 在 `App.vue` 导航中增加"录像管理"入口
- 从 `DevicesView.vue` 移除录像查询 tab
- 新页面功能对等：设备选择、时间范围查询、结果列表、点击播放、停止回放

**Non-Goals:**
- 后端接口不做任何修改
- 不引入新的前端依赖
- 不改变播放协议（保持 HTTP-FLV via flv.js）

## Decisions

**决策 1：抽取为独立 Vue 组件还是路由页面？**
选择独立路由页面（`/recordings`）。原因：
- 有自己独立的数据状态（录像列表、当前播放会话）
- 与设备列表的关联仅在于选择设备时需要设备列表，可通过 API 独立获取
- 用户可通过浏览器直接收藏/访问录像功能

**决策 2：设备列表如何获取？**
`RecordingView.vue` 独立调用 `/api/devices/local` 和 `/api/devices/remote` 拉取设备列表，填充设备选择下拉框。不依赖 DevicesView 的状态。

**决策 3：播放器嵌入位置**
与 DevicesView 保持一致：在录像列表下方内联嵌入 `<video>` 标签 + flv.js。点击录像条目的"播放"按钮时发起 playback/start，拿到 streamUrl 后初始化播放器。

**决策 4：如何迁移 DevicesView 中的录像代码？**
直接复制相关 template、data、methods 到 `RecordingView.vue`，再从 `DevicesView.vue` 删除录像 tab 相关代码。由于两边独立，无共享组件需要提取。

## Risks / Trade-offs

- [风险] `RecordingView.vue` 与 `DevicesView.vue` 的播放器逻辑重复 → 可接受，后续有需要再抽公共组件
- [风险] 删除 DevicesView 录像 tab 后，测试路径变化 → 测试直接访问 `/recordings` 即可

## Migration Plan

1. 新建 `RecordingView.vue`（含录像查询 + 回放功能）
2. 注册路由 `/recordings`
3. `App.vue` 导航增加"录像管理"入口
4. 从 `DevicesView.vue` 移除录像查询 tab 及其相关 data/methods

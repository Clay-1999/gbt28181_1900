## 1. 新建 RecordingView.vue

- [x] 1.1 新建 `frontend/src/views/RecordingView.vue`，页面布局：顶部查询条件区（设备选择 + 时间范围 + 查询按钮）、中部结果表格、底部播放器区域
- [x] 1.2 实现设备选择下拉框：页面挂载时分别调用 `/api/devices/local` 和 `/api/devices/remote` 获取设备列表，按"本端设备"/"外域设备"分组展示
- [x] 1.3 实现查询逻辑：点击查询时校验已选设备，调用 `POST /api/devices/{type}/{id}/records/query`（body：startTime/endTime/type="all"），展示录像条目表格（序号、录像名称、开始时间、结束时间、类型、操作列）
- [x] 1.4 处理查询状态：加载中 loading、504 超时显示警告、空结果显示"暂无录像"
- [x] 1.5 实现播放功能：录像条目"播放"按钮调用 `POST /api/devices/{type}/{id}/playback/start`（body：startTime/endTime），成功后用 flv.js 初始化播放器播放 streamUrl；"停止"按钮调用 playback/stop 并销毁播放器
- [x] 1.6 处理回放状态：回放加载中 loading、504 超时提示"回放启动超时"

## 2. 路由与导航

- [x] 2.1 在 `frontend/src/router/index.js` 中 import `RecordingView` 并注册路由 `{ path: '/recordings', component: RecordingView }`
- [x] 2.2 在 `frontend/src/App.vue` 的导航菜单中增加"录像管理"入口，路径 `/recordings`

## 3. 清理 DevicesView.vue

- [x] 3.1 从 `DevicesView.vue` 中移除"录像查询" `el-tab-pane`（name="record"）的全部模板代码
- [x] 3.2 从 `DevicesView.vue` 的 `data()` 中移除录像相关状态：`recordQuery`、`recordResults`、`recordLoading`、`recordTimeout`、`allDevices`、`playbackLoading`、`currentPlayback`、`currentFlvPlayer`
- [x] 3.3 从 `DevicesView.vue` 的 methods 中移除录像相关方法：`queryRecords`、`startPlayback`、`stopPlayback`、`loadAllDevices`（若仅录像使用）以及相关 flv.js 播放器管理代码
- [x] 3.4 确认 `DevicesView.vue` 中 `activeTab` 默认值不再是 `"record"`（改为 `"local"` 或保持当前值）

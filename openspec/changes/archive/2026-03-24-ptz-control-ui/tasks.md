## 1. PTZCmd 编码工具类

- [x] 1.1 新建 `PtzCmdEncoder.java`，实现 GB/T 28181 A.3 规定的 PTZCmd 8 字节编码：支持 8 方向（up/down/left/right/left-up/right-up/left-down/right-down）、停止、变倍（zoom_in/zoom_out）、变焦（focus_in/focus_out）、光圈（iris_in/iris_out）；speed 参数取值 0~255
- [x] 1.2 新建 `PtzControlRequest.java`（DTO），字段：`action`（String）、`speed`（Integer，缺省 128）、`presetIndex`（Integer，可选）、`presetName`（String，可选）、`trackName`（String，可选）

## 2. 后端：云台控制 REST 接口

- [x] 2.1 新建 `PtzService.java`（接口+实现），`sendPtzControl(type, deviceId, PtzControlRequest)` → 构建 DeviceControl XML，通过 `SipMessageSender` 发送，返回 `boolean`
- [x] 2.2 新建 `DeviceControlXml.java` JAXB 类，根元素 `<Control>`，包含 `CmdType=DeviceControl`、`SN`、`DeviceID`、`PTZCmd`（String）、`PTZCmdParams`（含 `PresetName`/`CruiseTrackName`）
- [x] 2.3 在 `DeviceController` 中新增端点：`POST /api/devices/{type}/{deviceId}/ptz/control`，调用 `PtzService`，返回 `{"success": true/false}`

## 3. 后端：预置位查询与控制接口

- [x] 3.1 新建 `PresetQueryXml.java` JAXB 类（`<Query>` 根元素，`CmdType=PresetQuery`、`SN`、`DeviceID`）
- [x] 3.2 新建 `PresetListResponse.java` JAXB 类，解析设备返回的预置位列表响应（`<Response>`，含 `<PresetList><Item><PresetIndex><PresetName></Item></PresetList>`）
- [x] 3.3 在 `PtzService` 中实现 `queryPresets(type, deviceId)` → 发送 `PresetQuery` SIP MESSAGE，等待响应（10s 超时），解析并返回列表；`callPreset(type, deviceId, presetIndex)` / `setPreset(type, deviceId, presetIndex, presetName)` / `deletePreset(type, deviceId, presetIndex)` → 发送对应 PTZCmd
- [x] 3.4 在 `DeviceController` 中新增端点：`GET /ptz/preset`、`POST /ptz/preset/call`、`POST /ptz/preset/set`、`POST /ptz/preset/delete`

## 4. 后端：巡航轨迹查询接口

- [x] 4.1 新建 `CruiseTrackListQueryXml.java` JAXB 类（`CmdType=CruiseTrackListQuery`）
- [x] 4.2 新建 `CruiseTrackQueryXml.java` JAXB 类（`CmdType=CruiseTrackQuery`，含 `Number` 字段）
- [x] 4.3 新建 `CruiseTrackListResponse.java` / `CruiseTrackDetailResponse.java` JAXB 类，解析设备响应
- [x] 4.4 在 `PtzService` 中实现 `queryCruiseTracks(type, deviceId)` / `queryCruiseTrack(type, deviceId, number)` / `startCruise(type, deviceId, trackName)` / `stopCruise(type, deviceId)`
- [x] 4.5 在 `DeviceController` 中新增端点：`GET /ptz/cruise`、`GET /ptz/cruise/{number}`、`POST /ptz/cruise/start`、`POST /ptz/cruise/stop`

## 5. 后端：SIP 响应路由（PTZ 查询回调）

- [x] 5.1 在 `GbtSipListener` / `DeviceCommandRouter` 中识别 `CmdType=PresetQuery`、`CruiseTrackListQuery`、`CruiseTrackQuery` 的响应 MESSAGE，通过 SN 匹配完成 `PtzService` 中对应的 `CompletableFuture`
- [x] 5.2 在 `PtzService` 中维护 `ConcurrentHashMap<String, CompletableFuture<String>> pending`，提供 `onResponse(sn, xmlBody)` 方法

## 6. 前端：实况播放对话框布局重构

- [x] 6.1 将播放对话框 `width` 改为计算属性：`isPtzDevice ? '1100px' : '720px'`；新增 `isPtzDevice` computed（判断 `streamDevice.ptzType` 是否在 `['1','4','5']` 中）
- [x] 6.2 对话框内容区改为 flex 横向布局：左侧视频区（`flex: 3`），右侧控制面板区（`flex: 2`，`v-if="isPtzDevice"`）
- [x] 6.3 右侧控制面板使用 `el-tabs`，包含"云台控制"、"预置位"、"巡航轨迹"三个 tab

## 7. 前端：云台控制面板

- [x] 7.1 实现 9 宫格方向键盘（8 方向 + 中心停止按钮），使用 CSS grid 3×3 布局
- [x] 7.2 每个方向按钮绑定 `@mousedown="ptzStart(action)"` 和 `@mouseup="ptzStop()"` / `@mouseleave="ptzStop()"` 事件
- [x] 7.3 实现变倍（+/-）、变焦（+/-）、光圈（+/-）三组按钮，同样绑定 mousedown/mouseup
- [x] 7.4 添加速度滑块（1~9），默认值 5，传入 ptzStart 请求 body
- [x] 7.5 实现 `ptzStart(action)` 和 `ptzStop()` 函数，调用 `/api/devices/{type}/{id}/ptz/control`

## 8. 前端：预置位管理面板

- [x] 8.1 切换到预置位 tab 时自动调用 `loadPresets()`，展示预置位列表（编号 + 名称 + 调用/删除按钮）
- [x] 8.2 实现"设置预置位"表单（编号输入框 + 名称输入框 + "保存当前位置"按钮），调用 `POST /ptz/preset/set`
- [x] 8.3 实现调用预置位（`POST /ptz/preset/call`）和删除预置位（`POST /ptz/preset/delete`，带二次确认）逻辑
- [x] 8.4 处理 504 超时场景：显示"设备不支持预置位查询"提示

## 9. 前端：巡航轨迹面板

- [x] 9.1 切换到巡航 tab 时自动调用 `loadCruiseTracks()`，展示轨迹列表（编号 + 名称 + 详情/启动巡航按钮）
- [x] 9.2 点击"详情"调用 `GET /ptz/cruise/{number}`，在 `el-dialog` 或展开行中显示轨迹包含的预置位序列
- [x] 9.3 实现启动巡航（`POST /ptz/cruise/start`，传入 trackName）和停止巡航（`POST /ptz/cruise/stop`）逻辑，按钮状态随巡航状态切换
- [x] 9.4 处理 504 超时场景：显示"设备不支持巡航轨迹查询"提示

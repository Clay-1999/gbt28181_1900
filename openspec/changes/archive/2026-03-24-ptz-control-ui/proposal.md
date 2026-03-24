## Why

当前实况播放界面仅展示视频画面，无法对球机/遥控枪机类型的摄像机执行云台控制、预置位管理和巡航轨迹配置。球机是监控场景中最常用的可控设备，缺少这些操作入口导致平台功能不完整。

## What Changes

- 实况播放对话框布局调整：视频区域与控制区域左右分栏，控制面板仅对球机/遥控类相机（PTZType 1/4/5）显示
- 新增云台控制面板：方向键盘（上/下/左/右/左上/右上/左下/右下/停止）+ 变倍/变焦/光圈调节按钮，通过 `PTZCmd` DeviceControl 报文发送
- 新增预置位管理面板：查询当前预置位列表（`PresetQuery`），支持调用预置位（`PTZCmd` goto preset）、设置预置位（`PTZCmd` set preset）、删除预置位
- 新增巡航轨迹面板：查询轨迹列表（`CruiseTrackListQuery`）、查询指定轨迹详情（`CruiseTrackQuery`）、启动/停止巡航

## Capabilities

### New Capabilities

- `ptz-control`: 云台控制指令的后端 REST 接口，接收方向/速度/动作参数，构建 GB/T 28181 `DeviceControl` SIP MESSAGE 并发送
- `ptz-preset`: 预置位查询（`PresetQuery`）与调用/设置/删除控制的后端接口
- `ptz-cruise`: 巡航轨迹列表查询（`CruiseTrackListQuery`）和单条轨迹查询（`CruiseTrackQuery`）的后端接口
- `ptz-control-ui`: 前端实况播放对话框布局重构 + 云台控制/预置位/巡航轨迹 UI 面板

### Modified Capabilities

- `config-ui`: 实况播放对话框的布局从纯视频改为左右分栏（视频 + 控制面板），对所有设备生效

## Impact

- 后端：新增 REST endpoints（`/api/devices/{type}/{id}/ptz/*`）
- 后端：新增 SIP MESSAGE 构建与发送逻辑（复用 `SipMessageSender`）
- 前端：`DevicesView.vue` 播放对话框模板和 script 大幅扩展
- 前端：新增依赖（无，复用 Element Plus 现有组件）

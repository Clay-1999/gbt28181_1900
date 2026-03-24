## Source: ptz-control

## ADDED Requirements

### Requirement: 云台方向控制接口
系统 SHALL 提供 REST 接口，接收云台控制动作并通过 GB/T 28181 `DeviceControl` SIP MESSAGE 发送 `PTZCmd`。

#### Scenario: 发送方向控制命令
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/control`，body 为 `{ "action": "left", "speed": 5 }`
- **THEN** 系统构建 `PTZCmd` 字节串，发送 DeviceControl SIP MESSAGE，返回 `{"success": true}`

#### Scenario: 发送停止命令
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/control`，body 为 `{ "action": "stop" }`
- **THEN** 系统发送 PTZCmd 停止帧，返回 `{"success": true}`

#### Scenario: 变倍控制
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/control`，body 为 `{ "action": "zoom_in", "speed": 3 }`
- **THEN** 系统发送对应变倍 PTZCmd，返回 `{"success": true}`

#### Scenario: 设备不存在
- **WHEN** 客户端传入不存在的 deviceId
- **THEN** 系统返回 HTTP 404

---

## Source: ptz-preset

## ADDED Requirements

### Requirement: 预置位查询接口
系统 SHALL 通过 GB/T 28181 `PresetQuery` SIP MESSAGE 查询目标设备的预置位列表，并以 JSON 返回。

#### Scenario: 查询预置位列表成功
- **WHEN** 客户端发送 `GET /api/devices/{type}/{deviceId}/ptz/preset`
- **THEN** 系统发送 `CmdType=PresetQuery` SIP MESSAGE，等待设备响应，返回预置位列表 JSON

#### Scenario: 查询超时
- **WHEN** 设备 10 秒内未响应
- **THEN** 系统返回 HTTP 504

### Requirement: 预置位调用接口
系统 SHALL 通过 `PTZCmd` 发送调用预置位命令，使球机转向指定预置位。

#### Scenario: 调用预置位
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/preset/call`，body 为 `{ "presetIndex": 1 }`
- **THEN** 系统发送 DeviceControl PTZCmd（调用预置位命令码），返回 `{"success": true}`

### Requirement: 预置位设置接口
系统 SHALL 通过 `PTZCmd` 发送设置预置位命令，将当前镜头位置保存为指定编号的预置位。

#### Scenario: 设置预置位
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/preset/set`，body 为 `{ "presetIndex": 1, "presetName": "大门" }`
- **THEN** 系统发送 DeviceControl PTZCmd（设置预置位命令码）及 PTZCmdParams，返回 `{"success": true}`

### Requirement: 预置位删除接口
系统 SHALL 通过 `PTZCmd` 发送删除预置位命令。

#### Scenario: 删除预置位
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/preset/delete`，body 为 `{ "presetIndex": 1 }`
- **THEN** 系统发送 DeviceControl PTZCmd（删除预置位命令码），返回 `{"success": true}`

---

## Source: ptz-cruise

## ADDED Requirements

### Requirement: 巡航轨迹列表查询接口
系统 SHALL 通过 GB/T 28181 `CruiseTrackListQuery` SIP MESSAGE 查询目标设备支持的巡航轨迹列表。

#### Scenario: 查询巡航轨迹列表成功
- **WHEN** 客户端发送 `GET /api/devices/{type}/{deviceId}/ptz/cruise`
- **THEN** 系统发送 `CmdType=CruiseTrackListQuery` SIP MESSAGE，等待设备响应，返回轨迹列表 JSON

#### Scenario: 查询超时
- **WHEN** 设备 10 秒内未响应
- **THEN** 系统返回 HTTP 504

### Requirement: 巡航轨迹详情查询接口
系统 SHALL 通过 `CruiseTrackQuery` SIP MESSAGE 查询指定编号的巡航轨迹详情（包含预置位序列）。

#### Scenario: 查询指定轨迹详情
- **WHEN** 客户端发送 `GET /api/devices/{type}/{deviceId}/ptz/cruise/{number}`
- **THEN** 系统发送 `CmdType=CruiseTrackQuery` 携带 `Number` 字段，返回轨迹详情 JSON

### Requirement: 巡航启动/停止控制接口
系统 SHALL 通过 `PTZCmd` 发送巡航启动或停止命令，并支持携带轨迹名称参数。

#### Scenario: 启动巡航
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/cruise/start`，body 为 `{ "trackName": "轨迹1" }`
- **THEN** 系统发送 DeviceControl PTZCmd（巡航启动命令码）及 PTZCmdParams.CruiseTrackName，返回 `{"success": true}`

#### Scenario: 停止巡航
- **WHEN** 客户端发送 `POST /api/devices/{type}/{deviceId}/ptz/cruise/stop`
- **THEN** 系统发送 DeviceControl PTZCmd（巡航停止命令码），返回 `{"success": true}`

---

## Source: northbound-ptz-receive

## ADDED Requirements

### Requirement: 接收上级 DeviceControl/PTZCmd 并路由执行
系统 SHALL 接受上级平台发来的 `DeviceControl` SIP MESSAGE，解析 `PTZCmd` 字段，按目标 `DeviceID` 路由到本端相机（通过 `PtzService`）或外域设备（通过 `RemoteDeviceMessageForwarder` 转发）。

#### Scenario: 接收本端相机 PTZ 命令
- **WHEN** 收到 SIP MESSAGE，CmdType=`DeviceControl`，DeviceID 为本端 `ivs1900_camera_mapping` 中的 gbDeviceId
- **THEN** 回复 `200 OK`，解析 `PTZCmd` 十六进制字节，调用 `PtzService` 执行对应动作（方向/速度/停止）

#### Scenario: 接收外域设备 PTZ 命令
- **WHEN** 收到 SIP MESSAGE，CmdType=`DeviceControl`，DeviceID 为 `remote_device` 中的设备 ID
- **THEN** 回复 `200 OK`，通过 `RemoteDeviceMessageForwarder` 将 DeviceControl 消息原样转发至对应下级平台

#### Scenario: 目标设备不存在
- **WHEN** 收到 DeviceControl，DeviceID 在本端和外域均无记录
- **THEN** 回复 `404 Not Found`，记录 WARN 日志

#### Scenario: PTZCmd 解析失败
- **WHEN** PTZCmd 字段格式不合法（非16进制或长度不符）
- **THEN** 回复 `200 OK`（避免上级重发），记录 WARN 日志，不执行动作

---

## Source: ptz-control-ui

## ADDED Requirements

### Requirement: 实况播放对话框左右分栏布局
对于球机/遥控类设备（PTZType 1/4/5），实况播放对话框 SHALL 采用左右分栏布局：左侧为视频区域（约 60% 宽），右侧为 PTZ 控制面板（约 40% 宽）。非可控设备保持原有全宽视频布局。

#### Scenario: 球机播放时显示控制面板
- **WHEN** 用户点击 PTZType 为 1/4/5 的设备"播放"按钮
- **THEN** 对话框宽度为 1100px，左侧显示视频，右侧显示控制面板（含云台/预置位/巡航三个 tab）

#### Scenario: 非球机播放时不显示控制面板
- **WHEN** 用户点击 PTZType 不在 1/4/5 的设备"播放"按钮
- **THEN** 对话框宽度为 720px，仅显示视频区域

### Requirement: 云台控制面板
控制面板 SHALL 提供云台方向控制（8 方向 + 停止）及变倍/变焦/光圈调节按钮。

#### Scenario: 按住方向键发送持续控制命令
- **WHEN** 用户按下某个方向按钮（mousedown）
- **THEN** 前端立即调用 `POST /ptz/control` 发送对应方向命令；用户松开（mouseup/mouseleave）时发送停止命令

#### Scenario: 变倍控制
- **WHEN** 用户点击"放大"或"缩小"按钮
- **THEN** 前端调用 `POST /ptz/control` 发送变倍命令

### Requirement: 预置位管理面板
控制面板 SHALL 提供预置位 tab，显示当前设备的预置位列表，并支持调用、设置、删除操作。

#### Scenario: 打开预置位 tab 自动加载
- **WHEN** 用户切换到预置位 tab
- **THEN** 前端调用 `GET /ptz/preset` 加载预置位列表并展示

#### Scenario: 调用预置位
- **WHEN** 用户点击列表中某预置位的"调用"按钮
- **THEN** 前端调用 `POST /ptz/preset/call`，成功后 ElMessage 提示"调用成功"

#### Scenario: 设置预置位
- **WHEN** 用户填写预置位编号和名称后点击"设置当前位置"
- **THEN** 前端调用 `POST /ptz/preset/set`，成功后刷新列表

#### Scenario: 删除预置位
- **WHEN** 用户点击某预置位的"删除"按钮并确认
- **THEN** 前端调用 `POST /ptz/preset/delete`，成功后从列表移除

### Requirement: 巡航轨迹面板
控制面板 SHALL 提供巡航轨迹 tab，显示轨迹列表，支持查看详情及启动/停止巡航。

#### Scenario: 打开巡航 tab 自动加载轨迹列表
- **WHEN** 用户切换到巡航轨迹 tab
- **THEN** 前端调用 `GET /ptz/cruise` 加载轨迹列表

#### Scenario: 查看轨迹详情
- **WHEN** 用户点击某轨迹"详情"
- **THEN** 前端调用 `GET /ptz/cruise/{number}`，以弹窗或展开行显示详情

#### Scenario: 启动巡航
- **WHEN** 用户选择轨迹并点击"启动巡航"
- **THEN** 前端调用 `POST /ptz/cruise/start`，返回成功后按钮切换为"停止巡航"

#### Scenario: 停止巡航
- **WHEN** 用户点击"停止巡航"
- **THEN** 前端调用 `POST /ptz/cruise/stop`

---

## Source: config-ui

## MODIFIED Requirements

### Requirement: 实况播放对话框布局
实况播放对话框 SHALL 根据设备类型动态调整布局：对球机/遥控类设备（PTZType 1/4/5）采用左右分栏（视频 + 控制面板），对其他设备保持全宽视频布局。对话框宽度随之从固定 720px 改为动态值（有控制面板时 1100px，无时 720px）。

#### Scenario: 球机播放时显示控制面板
- **WHEN** 用户点击 PTZType 为 1/4/5 的设备"播放"按钮
- **THEN** 对话框宽度为 1100px，左侧 60% 显示视频，右侧 40% 显示 PTZ 控制面板

#### Scenario: 非球机播放时不显示控制面板
- **WHEN** 用户点击 PTZType 不在 1/4/5 的设备"播放"按钮
- **THEN** 对话框宽度 720px，仅显示视频区域，行为与修改前一致
